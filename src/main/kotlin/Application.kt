import id.my.sendiko.plugins.*
import id.my.sendiko.services.*
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: System.getenv("JWT_SECRET") ?: "super-secret-jwt-key"
    val jwtDomain = environment.config.propertyOrNull("jwt.domain")?.getString()
        ?: System.getenv("JWT_DOMAIN") ?: "https://fintrack.sendiko.my.id/"
    val jwtAudience = environment.config.propertyOrNull("jwt.audience")?.getString()
        ?: System.getenv("JWT_AUDIENCE") ?: "fintrack-users"
    val jwtRealm = environment.config.propertyOrNull("jwt.realm")?.getString()
        ?: System.getenv("JWT_REALM") ?: "fintrack"

    val ollamaHost = environment.config.propertyOrNull("ollama.host")?.getString()
        ?: System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
    val ollamaModel = environment.config.propertyOrNull("ollama.model")?.getString()
        ?: System.getenv("OLLAMA_MODEL") ?: "llama3.2:3b"

    val jwtService = JwtService(jwtSecret, jwtDomain, jwtAudience, jwtRealm)
    val userService = UserService(jwtService)
    val walletService = WalletService()
    val categoryService = CategoryService()
    val transactionService = TransactionService()
    val receiptService = ReceiptService(ollamaHost, ollamaModel)
    val analysisService = AnalysisService()

    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting(
        userService = userService,
        walletService = walletService,
        categoryService = categoryService,
        transactionService = transactionService,
        receiptService = receiptService,
        analysisService = analysisService,
        jwtService = jwtService
    )
}
