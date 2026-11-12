package br.com.meutreino

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lê as corridas que o Polar Flow (ou outro app) gravou no Health Connect
 * e devolve para o index.html como JSON.
 */
class Saude(private val act: ComponentActivity, private val responder: (String) -> Unit) {

    private val permissoes = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class)
    )

    private var desdePendente: String = ""

    private val pedir: ActivityResultLauncher<Set<String>> =
        act.registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { dadas ->
            if (dadas.containsAll(permissoes)) ler(desdePendente)
            else responder(erro("sem_permissao", "Permissão não concedida. Toque de novo em Importar e marque Exercício e Distância."))
        }

    fun importar(desde: String, silencioso: Boolean) {
        val status = HealthConnectClient.getSdkStatus(act)
        if (status != HealthConnectClient.SDK_AVAILABLE) {
            if (!silencioso) responder(erro("indisponivel",
                if (status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
                    "Atualize o app Health Connect na Play Store e tente de novo."
                else "O Health Connect não está disponível neste celular."))
            return
        }
        val client = HealthConnectClient.getOrCreate(act)
        act.lifecycleScope.launch {
            try {
                val dadas = client.permissionController.getGrantedPermissions()
                if (dadas.containsAll(permissoes)) ler(desde)
                else if (!silencioso) { desdePendente = desde; pedir.launch(permissoes) }
            } catch (e: Exception) {
                if (!silencioso) responder(erro("falha", "Erro ao acessar o Health Connect: ${e.message}"))
            }
        }
    }

    private fun ler(desde: String) {
        val client = HealthConnectClient.getOrCreate(act)
        val zona = ZoneId.systemDefault()
        val inicio: Instant = try { LocalDate.parse(desde).atStartOfDay(zona).toInstant() }
                              catch (e: Exception) { Instant.now().minusSeconds(60L * 86400) }
        act.lifecycleScope.launch {
            try {
                val lista = JSONArray()
                var outros = 0
                var token: String? = null
                do {
                    val resp = client.readRecords(ReadRecordsRequest(
                        ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.after(inicio),
                        pageToken = token))
                    for (s in resp.records) {
                        val corrida = s.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING ||
                                      s.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
                        if (!corrida) { outros++; continue }
                        val agg = client.aggregate(AggregateRequest(
                            metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(s.startTime, s.endTime)))
                        val km = agg[DistanceRecord.DISTANCE_TOTAL]?.inKilometers ?: 0.0
                        val o = JSONObject()
                        o.put("id", s.metadata.id)
                        o.put("d", s.startTime.atZone(zona).toLocalDate().toString())
                        o.put("hora", s.startTime.atZone(zona).toLocalTime().toString().take(5))
                        o.put("km", km)
                        o.put("s", s.endTime.epochSecond - s.startTime.epochSecond)
                        o.put("esteira", s.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL)
                        o.put("app", s.metadata.dataOrigin.packageName)
                        lista.put(o)
                    }
                    token = resp.pageToken
                } while (!token.isNullOrEmpty())
                val r = JSONObject()
                r.put("ok", true); r.put("corridas", lista); r.put("outros", outros)
                responder(r.toString())
            } catch (e: Exception) {
                responder(erro("falha", "Erro ao ler as corridas: ${e.message}"))
            }
        }
    }

    private fun erro(cod: String, msg: String): String =
        JSONObject().put("ok", false).put("erro", cod).put("msg", msg).toString()
}
