import SwiftUI

struct ResearchGateView: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeaderBlock(
                    title: "Исследовательский режим",
                    subtitle: "Проверка правил допуска криптографических профилей и привязки контекста."
                )

                SectionBlock(title: "Текущий результат") {
                    KeyValueGrid(items: [
                        ("Профиль", profileTitle(store.state.admissionResult.profileId)),
                        ("Решение", store.state.admissionResult.decision.title),
                        ("Политика пакетов", store.state.admissionResult.decision.acceptedForPacketPolicy ? "разрешено" : "заблокировано"),
                    ])
                }

                DisclosureGroup("Технические данные") {
                    KeyValueGrid(items: [
                        ("ID профиля", store.state.admissionResult.profileId),
                        ("Хэш решения", store.state.admissionResult.decisionHash),
                        ("Ядро", store.state.admissionResult.nativeBackendVersion),
                    ])
                }

                SectionBlock(title: "Флаги риска") {
                    if store.state.admissionResult.riskFlags.isEmpty {
                        Text("Для текущего профиля флаги риска не найдены.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(store.state.admissionResult.riskFlags, id: \.self) { flag in
                            Label(riskFlagTitle(flag), systemImage: "exclamationmark.triangle")
                        }
                    }
                }

                HStack {
                    Button {
                        store.evaluateAdmission(experimental: true)
                    } label: {
                        Label("Принять профиль", systemImage: "checkmark.shield")
                    }

                    Button {
                        store.evaluateAdmission(experimental: false)
                    } label: {
                        Label("Отклонить рискованный", systemImage: "xmark.shield")
                    }
                }
            }
            .padding(24)
        }
        .navigationTitle("Исследования")
    }

    private func profileTitle(_ profileId: String) -> String {
        if profileId.contains("risk") {
            return "рискованный экспериментальный профиль"
        }
        if profileId.contains("experimental") {
            return "экспериментальный профиль"
        }
        return "стандартный профиль"
    }

    private func riskFlagTitle(_ flag: String) -> String {
        switch flag {
        case "rational_2_torsion":
            return "обнаружены точки кручения порядка 2"
        case "rational_3_torsion":
            return "обнаружены точки кручения порядка 3"
        default:
            return flag.replacingOccurrences(of: "_", with: " ")
        }
    }
}
