package com.disser.kraken.navigation

enum class KrakenRoute(
    val route: String,
    val label: String,
    val bottomNav: Boolean = false,
) {
    Welcome("welcome", "Старт"),
    Home("home", "Главная"),
    CreateIdentity("create-identity", "Профиль"),
    MyQr("my-qr", "QR"),
    ImportInvite("import-invite", "Импорт"),
    QrScanner("qr-scanner", "Скан QR"),
    Contacts("contacts", "Контакты", bottomNav = true),
    Realms("realms", "Реалмы", bottomNav = true),
    RealmManage("realm-manage", "Реалм"),
    PendingApprovals("pending-approvals", "Заявки"),
    Chat("chat", "Чаты", bottomNav = true),
    SavedMessages("saved-messages", "Избранное"),
    ContactProfile("contact-profile", "Профиль"),
    Channels("channels", "Каналы"),
    MeshStatus("mesh-status", "Сеть"),
    TwoPhoneChecklist("two-phone-checklist", "Проверка связи"),
    Settings("settings", "Настройки", bottomNav = true),
    ThemePicker("theme-picker", "Внешний вид"),
    Research("research", "Исследование"),
    UiLab("ui-lab", "Варианты интерфейса");

    companion object {
        val bottomRoutes = listOf(Chat, Contacts, Realms, Settings)

        fun fromRoute(route: String?): KrakenRoute? =
            entries.firstOrNull { it.route == route }
    }
}
