package id.my.sendiko.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtService(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String
) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier = JWT
        .require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun generateToken(userId: String, name: String, email: String): String {
        val validityMs = 30L * 24 * 60 * 60 * 1000 // 30 days
        val now = System.currentTimeMillis()
        return JWT.create()
            .withSubject(userId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("name", name)
            .withClaim("email", email)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + validityMs))
            .sign(algorithm)
    }
}
