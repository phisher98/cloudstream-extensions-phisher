import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import java.security.DigestException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class NinjaHD : NeoHD() {
    override var name = "NinjaHD"
    override var mainUrl = "https://ninjahd.one"
}

open class NeoHD : ExtractorApi() {
    override val name = "NeoHD"
    override val mainUrl = "https://neohd.xyz"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink> {
        val sources = mutableListOf<ExtractorLink>()
        val document = app.get(url).text
        val cryptoRegex = Regex("""var\s*playerConfig\s*=\s*([^;]+)""")
        val json = cryptoRegex.find(document)?.groupValues?.getOrNull(1).toString()
        val password = "F1r3b4Ll_GDP~5H".toByteArray()
        val data1 = parseJson<AesData>(json)
        val decryptedData =
            cryptoAESHandler(data1, password, false)?.replace("\\", "")?.substringAfter("\"")
                ?.substringBeforeLast("\"")
        val apiQuery = parseJson<CryptoResponse>(decryptedData!!).apiQuery
        val doc = app.get(
            url = "https://ninjahd.one/api/?$apiQuery&_=${System.currentTimeMillis() * 1000}",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to "https://ninjahd.one/embed/zilnv7x6da1s84"
            )
        ).text
        val source = parseJson<VideoUrl>(doc).sources[0].file
        sources.add(
            ExtractorLink(
                name,
                name,
                source,
                "$mainUrl/",
                Qualities.Unknown.value,
                headers = mapOf("range" to "bytes=0-")
            )
        )
        return sources
    }

    private fun GenerateKeyAndIv(
        password: ByteArray,
        salt: ByteArray,
        hashAlgorithm: String = "MD5",
        keyLength: Int = 32,
        ivLength: Int = 16,
        iterations: Int = 1
    ): List<ByteArray>? {

        val md = MessageDigest.getInstance(hashAlgorithm)
        val digestLength = md.digestLength
        val targetKeySize = keyLength + ivLength
        val requiredLength = (targetKeySize + digestLength - 1) / digestLength * digestLength
        val generatedData = ByteArray(requiredLength)
        var generatedLength = 0

        try {
            md.reset()

            while (generatedLength < targetKeySize) {
                if (generatedLength > 0)
                    md.update(
                        generatedData,
                        generatedLength - digestLength,
                        digestLength
                    )

                md.update(password)
                md.update(salt, 0, 8)
                md.digest(generatedData, generatedLength, digestLength)

                for (i in 1 until iterations) {
                    md.update(generatedData, generatedLength, digestLength)
                    md.digest(generatedData, generatedLength, digestLength)
                }

                generatedLength += digestLength
            }
            return listOf(
                generatedData.copyOfRange(0, keyLength),
                generatedData.copyOfRange(keyLength, targetKeySize)
            )
        } catch (e: DigestException) {
            return null
        }
    }

    private fun String.decodeHex(): ByteArray {
        check(length % 2 == 0) { "Must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun cryptoAESHandler(
        data: AesData,
        pass: ByteArray,
        encrypt: Boolean = true
    ): String? {
        val (key, iv) = GenerateKeyAndIv(pass, data.s.decodeHex()) ?: return null
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        return if (!encrypt) {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            String(cipher.doFinal(base64DecodeArray(data.ct)))
        } else {
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            base64Encode(cipher.doFinal(data.ct.toByteArray()))

        }
    }

    data class AesData(
        @JsonProperty("ct") var ct: String,
        @JsonProperty("iv") var iv: String,
        @JsonProperty("s") var s: String
    )

    data class CryptoResponse(
        @JsonProperty("apiQuery") var apiQuery: String
    )

    data class VideoUrl(
        @JsonProperty("sources") var sources: ArrayList<Sources> = arrayListOf(),
    )

    data class Sources(
        @JsonProperty("file") var file: String
    )
}
