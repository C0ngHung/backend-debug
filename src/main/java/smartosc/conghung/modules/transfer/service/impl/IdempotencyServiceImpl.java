package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.modules.transfer.dto.request.TransferRequest;
import smartosc.conghung.modules.transfer.entity.AppTransferRequest;
import smartosc.conghung.modules.transfer.repository.AppTransferRequestRepository;
import smartosc.conghung.modules.transfer.service.IdempotencyService;
import smartosc.conghung.modules.transfer.vo.ExternalReference;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "IDEMPOTENCY")
public class IdempotencyServiceImpl implements IdempotencyService {

    private static final String KEY_PREFIX = "APP_TRANSFER:";

    private final AppTransferRequestRepository transferRequestRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserve(ExternalReference ref, TransferRequest request) {

        // Tạo idempotency key bằng cách ghép prefix + externalRef (đã được strip() bởi Value Object)
        // Key này đóng vai trò "vé giữ chỗ" — nếu key đã tồn tại, nghĩa là giao dịch đã được xử lý rồi
        String idempotencyKey = KEY_PREFIX + ref.value();

        // Bước 1: Kiểm tra key đã tồn tại trong DB chưa
        // Nếu có → giao dịch trùng lặp (duplicate) → return false để caller bỏ qua
        if (transferRequestRepository.existsByIdempotencyKey(idempotencyKey)) {

            log.info("Duplicate transfer detected, skipping");

            return false;
        }

        // Bước 2: Chưa có key → tạo record mới với status = PROCESSING
        // Record này "giữ chỗ" — ngăn request thứ 2 trùng key đi tiếp
        // QUAN TRỌNG: Dùng REQUIRES_NEW để transaction này commit NGAY LẬP TỨC,
        // không phụ thuộc vào transaction cha (TransferService).
        // → Nếu transaction cha rollback, record idempotency vẫn còn trong DB
        AppTransferRequest transferRequest = AppTransferRequest.processing(
                ref.value(),
                idempotencyKey,
                request.getDebitAccountNo(),
                request.getAmount()
        );

        transferRequestRepository.save(transferRequest);

        log.info("Idempotency record reserved with key: {}", idempotencyKey);

        // return true = "đã giữ chỗ thành công", caller được phép tiếp tục gọi Core Banking
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(ExternalReference ref) {

        String idempotencyKey = KEY_PREFIX + ref.value();

        // Tìm record theo idempotency key, cập nhật status PROCESSING → SUCCESS
        // REQUIRES_NEW: commit riêng biệt, đảm bảo status SUCCESS được lưu
        // ngay cả khi transaction cha gặp lỗi sau đó
        transferRequestRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.markSuccess();
                    log.info("Transfer marked as SUCCESS");
                });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnknown(ExternalReference ref) {

        String idempotencyKey = KEY_PREFIX + ref.value();

        // Khi gọi Core Banking bị timeout (ResourceAccessException):
        // - Không biết Core đã trừ tiền chưa → đánh dấu UNKNOWN
        // - REQUIRES_NEW: commit riêng biệt, đảm bảo ghi nhận trạng thái ngay
        // - Record UNKNOWN cần được đối soát (reconciliation) thủ công sau
        transferRequestRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.markUnknown();
                    log.info("Transfer marked as UNKNOWN — requires reconciliation");
                });
    }
}
