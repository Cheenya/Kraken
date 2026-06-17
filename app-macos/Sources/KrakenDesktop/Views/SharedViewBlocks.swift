import SwiftUI

struct HeaderBlock: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.largeTitle.weight(.semibold))
            Text(subtitle)
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

struct SectionBlock<Content: View>: View {
    @Environment(\.krakenPalette) private var palette
    let title: String
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
            content
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(palette.panelBackground, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(palette.accent.opacity(0.18), lineWidth: 1)
        }
    }
}

struct KeyValueGrid: View {
    let items: [(String, String)]

    var body: some View {
        Grid(alignment: .leading, horizontalSpacing: 22, verticalSpacing: 8) {
            ForEach(items, id: \.0) { item in
                GridRow {
                    Text(item.0)
                        .foregroundStyle(.secondary)
                    Text(item.1)
                        .textSelection(.enabled)
                }
            }
        }
    }
}
