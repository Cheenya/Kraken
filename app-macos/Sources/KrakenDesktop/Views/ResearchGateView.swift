import SwiftUI

struct ResearchGateView: View {
    @EnvironmentObject private var store: KrakenDesktopStore

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HeaderBlock(
                    title: "Исследовательский режим",
                    subtitle: "Проверка правил допуска профилей. Это исследовательский прототип, не промышленная криптография."
                )

                SectionBlock(title: "Текущий результат") {
                    KeyValueGrid(items: [
                        ("Профиль", store.state.admissionResult.profileId),
                        ("Решение", store.state.admissionResult.decision.title),
                        ("Хэш решения", store.state.admissionResult.decisionHash),
                        ("Ядро", store.state.admissionResult.nativeBackendVersion),
                        ("Политика пакетов", store.state.admissionResult.decision.acceptedForPacketPolicy ? "разрешено" : "заблокировано"),
                    ])
                }

                SectionBlock(title: "Флаги риска") {
                    if store.state.admissionResult.riskFlags.isEmpty {
                        Text("Для текущего профиля флаги риска не найдены.")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(store.state.admissionResult.riskFlags, id: \.self) { flag in
                            Label(flag, systemImage: "exclamationmark.triangle")
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
}
