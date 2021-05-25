package com.mycelium.giftbox.cardDetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.mycelium.wallet.*
import com.mycelium.wallet.activity.util.toString
import com.mycelium.wallet.databinding.FragmentGiftboxAmountBinding
import com.mycelium.wapi.api.lib.CurrencyCode
import com.mycelium.wapi.wallet.coins.Value
import com.mycelium.wapi.wallet.coins.Value.Companion.isNullOrZero
import com.mycelium.wapi.wallet.coins.Value.Companion.valueOf
import kotlinx.android.synthetic.main.layout_fio_request_notification.*

class AmountInputFragment : Fragment(), NumberEntry.NumberEntryListener {
    private lateinit var binding: FragmentGiftboxAmountBinding
    private var _numberEntry: NumberEntry? = null

    private lateinit var _mbwManager: MbwManager
    val args by navArgs<AmountInputFragmentArgs>()
    private var _amount: Value =
        Value(Utils.getTypeByName(CurrencyCode.USD.shortString)!!, 0.toBigInteger())
        set(value) {
            field = value
            binding.tvAmount.text = value.toFriendlyString()
        }



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate<FragmentGiftboxAmountBinding>(
            inflater,
            R.layout.fragment_giftbox_amount,
            container,
            false
        ).apply { lifecycleOwner = this@AmountInputFragment }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _mbwManager = MbwManager.getInstance(activity?.applicationContext)
        with(binding) {
            btOk.setOnClickListener {
                setFragmentResult(REQUEST_AMOUNT, bundleOf(AMOUNT_KEY to _amount))
                findNavController().navigateUp()
            }
            btMax.setOnClickListener {
                _amount = valueOf(_amount.type, args.product.maximum_value!!)
            }
        }

        initNumberEntry(savedInstanceState)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putSerializable(ENTERED_AMOUNT, _amount)
    }

    private fun initNumberEntry(savedInstanceState: Bundle?) {
        // Load saved state
        if (savedInstanceState != null) {
            _amount = savedInstanceState.getSerializable(ENTERED_AMOUNT) as Value
        }

        // Init the number pad
        val amountString: String
        if (!isNullOrZero(_amount)) {
            amountString = _amount.toString(_mbwManager.getDenomination(_amount.type))
        } else {
            amountString = ""
        }
        _numberEntry = NumberEntry(2, this, activity, amountString)
    }


    override fun onEntryChanged(entry: String, wasSet: Boolean) {
        if (!wasSet) {
            // if it was change by the user pressing buttons (show it unformatted)
            setEnteredAmount(entry)
        }
        checkEntry()
    }

    private fun setEnteredAmount(value: String) {
        _amount = valueOf(_amount.type, value)
    }

    private fun checkEntry() {
        val valid = !isNullOrZero(_amount)
        binding.btOk.isEnabled = !valid
    }


    companion object {
        const val REQUEST_AMOUNT = "request_amount"
        const val AMOUNT_KEY = "amount"
        const val ENTERED_AMOUNT = "enteredamount"
    }

}