package com.nexters.gamss.wallet.service

import com.nexters.gamss.wallet.repository.TokenWalletRepository
import org.springframework.stereotype.Service

@Service
class TokenWalletReader(
    private val walletRepository: TokenWalletRepository,
) {
    fun balanceOf(memberId: Long): Long = walletRepository.findBalance(memberId)
}
