package br.com.nexo.driver.overlay

/** Driver-selected vertical placement for the read-only offer overlay. */
enum class OverlayPosition(
    val label: String,
) {
    TOP("Topo"),
    BOTTOM("Rodapé"),
}
