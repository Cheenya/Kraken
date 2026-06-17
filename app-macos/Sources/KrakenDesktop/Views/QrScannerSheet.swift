import AppKit
import AVFoundation
import SwiftUI
import Vision

struct QrScannerSheet: View {
    @Environment(\.krakenPalette) private var palette
    let closeAction: () -> Void
    let importAction: (String) -> String?
    @State private var scannerId = UUID()
    @State private var errorMessage: String?
    @State private var lastScannedPayload: String?
    @State private var manualPayload = ""
    @State private var scanLocked = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Сканировать QR")
                        .font(.title2.weight(.semibold))
                    Text("Наведите камеру Mac на QR второго устройства.")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button(action: closeAction) {
                    ZStack {
                        Circle()
                            .fill(Color.secondary.opacity(0.14))
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                    }
                    .frame(width: 38, height: 38)
                    .contentShape(Circle())
                }
                .buttonStyle(.plain)
                .help("Закрыть")
                .keyboardShortcut(.cancelAction)
            }

            ZStack(alignment: .bottomLeading) {
                CameraQrScannerView(
                    onCode: handleCode(_:),
                    onError: { message in
                        errorMessage = message
                        scanLocked = true
                    }
                )
                .id(scannerId)
                .frame(height: 360)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(palette.accent.opacity(0.32), lineWidth: 1)
                }

                VStack(alignment: .leading, spacing: 8) {
                    if let errorMessage {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.orange)
                            .font(.callout.weight(.semibold))
                            .fixedSize(horizontal: false, vertical: true)

                        if let lastScannedPayload {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(scanSummary(for: lastScannedPayload))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                Text(payloadPreview(for: lastScannedPayload))
                                    .font(.caption.monospaced())
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                    .textSelection(.enabled)
                            }
                        }

                        HStack(spacing: 10) {
                            Button {
                                self.errorMessage = nil
                                lastScannedPayload = nil
                                scanLocked = false
                                scannerId = UUID()
                            } label: {
                                Label("Сканировать ещё раз", systemImage: "arrow.clockwise")
                            }
                            .buttonStyle(.borderedProminent)

                            if let lastScannedPayload {
                                Button {
                                    NSPasteboard.general.clearContents()
                                    NSPasteboard.general.setString(lastScannedPayload, forType: .string)
                                } label: {
                                    Label("Скопировать payload", systemImage: "doc.on.doc")
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    } else {
                        Label("Ожидание QR", systemImage: "qrcode.viewfinder")
                            .foregroundStyle(.secondary)
                            .font(.callout.weight(.semibold))
                    }
                }
                .padding(14)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
                .padding(14)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text("Вставить QR вручную")
                    .font(.headline.weight(.semibold))

                TextEditor(text: $manualPayload)
                    .font(.system(.callout, design: .monospaced))
                    .scrollContentBackground(.hidden)
                    .frame(height: 82)
                    .padding(10)
                    .background(palette.inputBackground, in: RoundedRectangle(cornerRadius: 8))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(palette.accent.opacity(0.22), lineWidth: 1)
                    }

                HStack(spacing: 10) {
                    Button {
                        manualPayload = NSPasteboard.general.string(forType: .string) ?? ""
                    } label: {
                        Label("Вставить из буфера", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.bordered)

                    Button {
                        handleManualImport()
                    } label: {
                        Label("Импортировать", systemImage: "square.and.arrow.down")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(manualPayload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                    Spacer()
                }
            }
        }
        .padding(24)
        .frame(width: 720)
    }

    private func handleCode(_ code: String) {
        guard !scanLocked else { return }
        scanLocked = true
        submitPayload(code)
    }

    private func handleManualImport() {
        let payload = manualPayload.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !payload.isEmpty else {
            errorMessage = "Вставьте QR payload или ссылку Kraken."
            return
        }
        scanLocked = true
        submitPayload(payload)
    }

    private func submitPayload(_ payload: String) {
        lastScannedPayload = payload
        if let error = importAction(payload) {
            errorMessage = error
        } else {
            closeAction()
        }
    }

    private func scanSummary(for payload: String) -> String {
        let trimmed = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        let kind: String
        if trimmed.hasPrefix("{") {
            kind = "похоже на JSON-объект"
        } else if trimmed.hasPrefix("[") {
            kind = "JSON-массив"
        } else if URLComponents(string: trimmed)?.scheme != nil {
            kind = "URL"
        } else if trimmed.isEmpty {
            kind = "пустая строка"
        } else {
            kind = "текст"
        }
        return "Считано: \(payload.count) символов, формат: \(kind)."
    }

    private func payloadPreview(for payload: String) -> String {
        let normalized = payload
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
        let limit = 220
        if normalized.count <= limit {
            return normalized.isEmpty ? "<пусто>" : normalized
        }
        let endIndex = normalized.index(normalized.startIndex, offsetBy: limit)
        return "\(normalized[..<endIndex])..."
    }
}

private struct CameraQrScannerView: NSViewRepresentable {
    let onCode: (String) -> Void
    let onError: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCode: onCode, onError: onError)
    }

    func makeNSView(context: Context) -> CameraPreviewView {
        let view = CameraPreviewView()
        context.coordinator.prepare(on: view)
        return view
    }

    func updateNSView(_ nsView: CameraPreviewView, context: Context) {}

    static func dismantleNSView(_ nsView: CameraPreviewView, coordinator: Coordinator) {
        coordinator.stop()
    }

    final class Coordinator: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
        private let onCode: (String) -> Void
        private let onError: (String) -> Void
        private let queue = DispatchQueue(label: "kraken.desktop.qr.scanner")
        private var session: AVCaptureSession?
        private var didReportCode = false
        private var frameCounter = 0

        init(onCode: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
            self.onCode = onCode
            self.onError = onError
        }

        func prepare(on view: CameraPreviewView) {
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized:
                configure(on: view)
            case .notDetermined:
                AVCaptureDevice.requestAccess(for: .video) { [weak self, weak view] granted in
                    DispatchQueue.main.async {
                        guard let self, let view else { return }
                        if granted {
                            self.configure(on: view)
                        } else {
                            self.onError("Доступ к камере запрещён.")
                        }
                    }
                }
            case .denied, .restricted:
                onError("Доступ к камере запрещён в настройках macOS.")
            @unknown default:
                onError("Камера недоступна.")
            }
        }

        func stop() {
            let currentSession = session
            queue.async {
                currentSession?.stopRunning()
            }
            session = nil
        }

        private func configure(on view: CameraPreviewView) {
            guard session == nil else { return }
            guard let device = AVCaptureDevice.default(for: .video) else {
                onError("Камера не найдена.")
                return
            }

            do {
                let input = try AVCaptureDeviceInput(device: device)
                let nextSession = AVCaptureSession()
                nextSession.sessionPreset = .high
                guard nextSession.canAddInput(input) else {
                    onError("Камеру не удалось подключить.")
                    return
                }
                nextSession.addInput(input)

                let videoOutput = AVCaptureVideoDataOutput()
                videoOutput.alwaysDiscardsLateVideoFrames = true
                videoOutput.videoSettings = [
                    kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
                ]
                guard nextSession.canAddOutput(videoOutput) else {
                    onError("Кадры камеры недоступны.")
                    return
                }
                nextSession.addOutput(videoOutput)
                videoOutput.setSampleBufferDelegate(self, queue: queue)

                let previewLayer = AVCaptureVideoPreviewLayer(session: nextSession)
                previewLayer.videoGravity = .resizeAspectFill
                view.previewLayer = previewLayer
                session = nextSession

                queue.async {
                    nextSession.startRunning()
                }
            } catch {
                onError("Камеру не удалось открыть: \(error.localizedDescription)")
            }
        }

        func captureOutput(
            _ output: AVCaptureOutput,
            didOutput sampleBuffer: CMSampleBuffer,
            from connection: AVCaptureConnection
        ) {
            guard !didReportCode else { return }
            frameCounter += 1
            guard frameCounter % 3 == 0 else { return }

            let request = VNDetectBarcodesRequest { [weak self] request, error in
                guard let self, !self.didReportCode else { return }
                if let error {
                    DispatchQueue.main.async {
                        self.onError("Ошибка распознавания QR: \(error.localizedDescription)")
                    }
                    return
                }
                guard let observation = (request.results as? [VNBarcodeObservation])?
                    .first(where: { $0.symbology == .qr }),
                    let value = observation.payloadStringValue
                else {
                    return
                }
                self.didReportCode = true
                self.stop()
                DispatchQueue.main.async {
                    self.onCode(value)
                }
            }
            request.symbologies = [.qr]

            do {
                try VNImageRequestHandler(
                    cmSampleBuffer: sampleBuffer,
                    orientation: .up,
                    options: [:]
                ).perform([request])
            } catch {
                DispatchQueue.main.async {
                    self.onError("Кадр камеры не удалось обработать: \(error.localizedDescription)")
                }
            }
        }
    }
}

private final class CameraPreviewView: NSView {
    var previewLayer: AVCaptureVideoPreviewLayer? {
        didSet {
            oldValue?.removeFromSuperlayer()
            if let previewLayer {
                wantsLayer = true
                layer?.addSublayer(previewLayer)
                needsLayout = true
            }
        }
    }

    override func layout() {
        super.layout()
        previewLayer?.frame = bounds
    }
}
