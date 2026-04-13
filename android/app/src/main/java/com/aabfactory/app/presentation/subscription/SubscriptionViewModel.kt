package com.aabfactory.app.presentation.subscription

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aabfactory.app.data.model.Plan
import com.aabfactory.app.data.model.UserPlan
import com.aabfactory.app.data.repository.AuthRepository
import com.aabfactory.app.data.repository.SubscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubscriptionState(
    val userPlan: UserPlan = UserPlan(),
    val isLoading: Boolean = true,
    val isProcessingPayment: Boolean = false,
    val checkoutUrl: String = "",
    val error: String? = null
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val subscriptionRepository: SubscriptionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()

    private val uid get() = authRepository.currentUser?.uid ?: ""

    init {
        observePlan()
    }

    private fun observePlan() {
        subscriptionRepository.observeUserPlan(uid)
            .onEach { plan ->
                _state.value = _state.value.copy(userPlan = plan, isLoading = false)
            }
            .launchIn(viewModelScope)
    }

    fun subscribePro() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessingPayment = true, error = null)
            val result = subscriptionRepository.createCheckoutSession("pro_monthly")
            result.fold(
                onSuccess = { url ->
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        checkoutUrl = url
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isProcessingPayment = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun purchaseBuildPack() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isProcessingPayment = true, error = null)
            val result = subscriptionRepository.createCheckoutSession("build_pack_5")
            result.fold(
                onSuccess = { url ->
                    _state.value = _state.value.copy(isProcessingPayment = false, checkoutUrl = url)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isProcessingPayment = false, error = e.message)
                }
            )
        }
    }

    fun clearCheckoutUrl() {
        _state.value = _state.value.copy(checkoutUrl = "")
    }
}
