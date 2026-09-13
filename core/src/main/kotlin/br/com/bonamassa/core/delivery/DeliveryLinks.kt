package br.com.bonamassa.core.delivery

import java.net.URLEncoder

object DeliveryLinks {
    private fun encoded(address: String): String {
        require(address.isNotBlank() && address.length <= 300)
        return URLEncoder.encode(address, "UTF-8")
    }
    fun googleMaps(address: String) = "https://www.google.com/maps/dir/?api=1&destination=${encoded(address)}&travelmode=driving"
    fun waze(address: String) = "https://waze.com/ul?q=${encoded(address)}&navigate=yes"

    fun dialNumber(phone: String): String? {
        if (phone.any { !it.isDigit() && it !in "+() -" }) return null
        val digits = phone.filter { it in '0'..'9' }
        return when {
            digits.length in 10..11 && !phone.trim().startsWith('+') -> digits
            digits.length in 12..13 && digits.startsWith("55") -> "+$digits"
            else -> null
        }
    }
}
