package com.mycelium.wallet.external.changelly2.viewmodel

import android.util.Log
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import com.mrd.bitlib.TransactionUtils
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.WalletApplication
import com.mycelium.wallet.activity.util.toStringFriendlyWithUnit
import com.mycelium.wallet.activity.util.toStringWithUnit
import com.mycelium.wallet.external.changelly.model.FixRate
import com.mycelium.wapi.wallet.Transaction
import com.mycelium.wapi.wallet.Util
import com.mycelium.wapi.wallet.WalletAccount
import com.mycelium.wapi.wallet.btc.FeePerKbFee
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.exceptions.BuildTransactionException
import com.mycelium.wapi.wallet.exceptions.InsufficientFundsException
import com.mycelium.wapi.wallet.exceptions.OutputTooSmallException


class ExchangeViewModel : ViewModel() {
    val mbwManager = MbwManager.getInstance(WalletApplication.getInstance())
    var currencies = setOf("BTC", "ETH")
    val fromAccount = MutableLiveData<WalletAccount<*>>()
    val exchangeInfo = MutableLiveData<FixRate>()
    val sellValue = MutableLiveData<String>()
    val buyValue = MutableLiveData<String>()
    val errorKeyboard = MutableLiveData("")
    val errorTransaction = MutableLiveData("")
    val errorRemote = MutableLiveData("")

    var changellyTx: String? = null

    val toAccount = MediatorLiveData<WalletAccount<*>>().apply {
        addSource(fromAccount) {
            if (value?.coinType == it.coinType) {
                value = getToAccount()
            }
        }
    }

    val error = MediatorLiveData<String>().apply {
        value = ""
        fun error() =
                when {
                    errorKeyboard.value?.isNotEmpty() == true -> errorKeyboard.value
                    errorTransaction.value?.isNotEmpty() == true -> errorTransaction.value
                    errorRemote.value?.isNotEmpty() == true -> errorRemote.value
                    else -> ""
                }
        addSource(errorKeyboard) {
            value = error()
        }
        addSource(errorTransaction) {
            value = error()
        }
    }


    val fromCurrency = Transformations.map(fromAccount) {
        it.coinType
    }
    val fromAddress = Transformations.map(fromAccount) {
        it.receiveAddress.toString()
    }
    val fromChain = Transformations.map(fromAccount) {
        if (it.basedOnCoinType != it.coinType) it.basedOnCoinType.name else ""
    }
    val fromFiatBalance = Transformations.map(fromAccount) {
        mbwManager.exchangeRateManager
                .get(it.accountBalance.spendable, mbwManager.getFiatCurrency(it.coinType))
                ?.toStringFriendlyWithUnit()
    }
    val toCurrency = Transformations.map(toAccount) {
        it?.coinType ?: Utils.getBtcCoinType()
    }
    val toAddress = Transformations.map(toAccount) {
        it?.receiveAddress?.toString()
    }
    val toChain = Transformations.map(toAccount) {
        if (it?.basedOnCoinType != it?.coinType) it?.basedOnCoinType?.name else ""
    }
    val toBalance = Transformations.map(toAccount) {
        it?.accountBalance?.spendable?.toStringFriendlyWithUnit()
    }
    val toFiatBalance = Transformations.map(toAccount) {
        it?.accountBalance?.spendable?.let { value ->
            mbwManager.exchangeRateManager
                    .get(value, mbwManager.getFiatCurrency(it.coinType))
                    ?.toStringFriendlyWithUnit()
        }
    }
    val exchangeRate = Transformations.map(exchangeInfo) {
        "1 ${it.from.toUpperCase()} = ${it.result} ${it.to.toUpperCase()}"
    }
    val fiatSellValue = Transformations.map(sellValue) {
        if (it?.isNotEmpty() == true) {
            try {
                mbwManager.exchangeRateManager
                        .get(fromCurrency.value?.value(it), mbwManager.getFiatCurrency(fromCurrency.value))
                        ?.toStringFriendlyWithUnit()
            } catch (e: NumberFormatException) {
                "N/A"
            }
        } else {
            ""
        }
    }
    val fiatBuyValue = Transformations.map(buyValue) {
        if (it?.isNotEmpty() == true) {
            try {
                mbwManager.exchangeRateManager
                        .get(toCurrency.value?.value(it), mbwManager.getFiatCurrency(toCurrency.value))
                        ?.toStringFriendlyWithUnit()
            } catch (e: NumberFormatException) {
                "N/A"
            }
        } else {
            ""
        }
    }

    val validateData = MediatorLiveData<Boolean>().apply {
        value = isValid()
        addSource(sellValue) {
            value = isValid()
        }
        addSource(exchangeInfo) {
            value = isValid()
        }
    }

    fun isValid(): Boolean =
            try {
                val amount = sellValue.value?.toBigDecimal()
                when {
                    amount == null -> false
                    amount < exchangeInfo.value?.minFrom -> false
                    amount > exchangeInfo.value?.maxFrom -> false
                    else -> checkValidTransaction() != null
                }
            } catch (e: java.lang.NumberFormatException) {
                false
            }

    fun checkValidTransaction(): Transaction? {
        val res = WalletApplication.getInstance().resources
        val account = fromAccount.value!!
        val value = account.coinType.value(sellValue.value!!)
        if (value.equalZero()) {
            errorTransaction.value = ""
            return null
        }
        try {
            val feeEstimation = mbwManager.getFeeProvider(account.basedOnCoinType).estimation
            return account.createTx(
                    account.dummyAddress,
                    value,
                    FeePerKbFee(feeEstimation.normal),
                    null
            ).apply {
                errorTransaction.value = ""
            }
        } catch (e: OutputTooSmallException) {
            errorTransaction.value = res.getString(R.string.amount_too_small_short,
                    Value.valueOf(account.coinType, TransactionUtils.MINIMUM_OUTPUT_VALUE).toStringWithUnit())
        } catch (e: InsufficientFundsException) {
            errorTransaction.value = res.getString(R.string.insufficient_funds)
        } catch (e: BuildTransactionException) {
            mbwManager.reportIgnoredException("MinerFeeException", e)
            errorTransaction.value = res.getString(R.string.tx_build_error) + " " + e.message
        } catch (e: Exception) {
            Log.e("!!!", "", e)
            errorTransaction.value = res.getString(R.string.tx_build_error) + " " + e.message
        }
        return null
    }

    fun getToAccount() = Utils.sortAccounts(mbwManager.getWalletManager(false)
            .getAllActiveAccounts(), mbwManager.metadataStorage)
            .firstOrNull {
                it.coinType != fromAccount.value?.coinType
                        && currencies.contains(Util.trimTestnetSymbolDecoration(it.coinType.symbol).toLowerCase())
            }
}