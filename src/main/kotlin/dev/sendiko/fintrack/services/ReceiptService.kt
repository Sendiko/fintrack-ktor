package dev.sendiko.fintrack.services

import dev.sendiko.fintrack.models.ReceiptDataDto
import dev.sendiko.fintrack.models.ReceiptProductDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class ReceiptService(
    private val ollamaHost: String = "http://localhost:11434",
    private val ollamaModel: String = "llama3.2:3b"
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun extractReceipt(receiptText: String): Result<ReceiptDataDto> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${ollamaHost.trimEnd('/')}/api/generate"

            val formatSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("receipt_name") {
                        put("type", "string")
                        put("description", "The merchant or store name.")
                    }
                    putJsonObject("total_price") {
                        put("type", "number")
                        put("description", "The total grand amount paid.")
                    }
                    putJsonObject("products") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("name") { put("type", "string") }
                                putJsonObject("price") { put("type", "number") }
                            }
                            putJsonArray("required") {
                                add("name")
                                add("price")
                            }
                        }
                    }
                }
                putJsonArray("required") {
                    add("receipt_name")
                    add("total_price")
                    add("products")
                }
            }

            val requestBody = buildJsonObject {
                put("model", ollamaModel)
                put("system", "You are an Indonesian receipt parser. Extract data accurately. Note: Periods in Indonesian prices represent thousands (e.g., 38.400 means 38400) - return them as pure numbers.")
                put("prompt", "Extract from this raw text:\n\n$receiptText")
                put("stream", false)
                putJsonObject("options") {
                    put("temperature", 0.0)
                }
                put("format", formatSchema)
            }.toString()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                return@withContext Result.failure(
                    RuntimeException("Ollama API failed with status ${response.statusCode()}: ${response.body()}")
                )
            }

            val responseJson = json.parseToJsonElement(response.body()).jsonObject
            val responseText = responseJson["response"]?.jsonPrimitive?.content
                ?: return@withContext Result.failure(RuntimeException("No 'response' found in Ollama output"))

            val extractedJson = json.parseToJsonElement(responseText).jsonObject
            val receiptName = extractedJson["receipt_name"]?.jsonPrimitive?.content ?: "Unknown Merchant"
            val totalPrice = extractedJson["total_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            val products = extractedJson["products"]?.jsonArray?.mapNotNull { item ->
                val obj = item.jsonObject
                val pName = obj["name"]?.jsonPrimitive?.content
                val pPrice = obj["price"]?.jsonPrimitive?.doubleOrNull
                if (pName != null && pPrice != null) {
                    ReceiptProductDto(name = pName, price = pPrice)
                } else null
            } ?: emptyList()

            Result.success(
                ReceiptDataDto(
                    receipt_name = receiptName,
                    total_price = totalPrice,
                    products = products
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
