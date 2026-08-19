package com.nexters.gamss.global.crypto

/** 암복호화 단위 테스트가 공유하는 더미 키(Base64 32바이트). 실제 키가 아니다. */
const val DATA_KEY = "Z2Ftc3MtdGVzdC1kYXRhLWVuY3J5cHRpb24ta2V5ISE="
const val INDEX_KEY = "Z2Ftc3MtdGVzdC1zZWFyY2gtaW5kZXgta2V5ISEhISE="
const val OTHER_DATA_KEY = "Z2Ftc3MtdGVzdC1vdGhlci1kYXRhLWtleSEhISEhISE="
const val OTHER_INDEX_KEY = "Z2Ftc3MtdGVzdC1vdGhlci1pbmRleC1rZXkhISEhISE="
const val SHORT_KEY = "dG9vLXNob3J0LWtleQ=="

fun testProperties(
    dataKey: String = DATA_KEY,
    indexKey: String = INDEX_KEY,
    keyVersion: String = "v1",
) = EncryptionProperties(dataKey = dataKey, indexKey = indexKey, keyVersion = keyVersion)
