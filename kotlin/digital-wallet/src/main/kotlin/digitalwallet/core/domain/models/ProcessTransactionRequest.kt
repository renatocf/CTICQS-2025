package digitalwallet.core.domain.models

import digitalwallet.core.domain.enums.SubwalletType
import digitalwallet.core.domain.enums.TransactionType
import java.math.BigDecimal

data class ProcessTransactionRequest(
    val amount: BigDecimal,
    val batchId: String? = null,
    val idempotencyKey: String,
    val originatorWalletId: String,
    val originatorSubwalletType: SubwalletType,
    val beneficiaryWalletId: String? = null,
    val beneficiarySubwalletType: SubwalletType? = null,
    val type: TransactionType,
    val metadata: TransactionMetadata? = null
)