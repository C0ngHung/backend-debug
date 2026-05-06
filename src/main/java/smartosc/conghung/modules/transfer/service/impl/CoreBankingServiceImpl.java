package smartosc.conghung.modules.transfer.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.modules.transfer.dto.request.CoreDebitRequest;
import smartosc.conghung.modules.transfer.entity.CoreDebitLog;
import smartosc.conghung.modules.transfer.repository.CoreAccountRepository;
import smartosc.conghung.modules.transfer.repository.CoreDebitLogRepository;
import smartosc.conghung.modules.transfer.service.CoreBankingService;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "CORE-BANKING")
public class CoreBankingServiceImpl implements CoreBankingService {

    private final CoreAccountRepository coreAccountRepository;
    private final CoreDebitLogRepository coreDebitLogRepository;

    @Override
    @Transactional
    public void debit(CoreDebitRequest request) {

        // Bước 0: Giả lập độ trễ mạng (latency) khi gọi Core Banking
        // Nếu delay > readTimeout (2000ms) của client → client sẽ nhận timeout exception
        // NHƯNG Core vẫn tiếp tục xử lý → đây chính là root cause của Ghost Double Debit
        sleep(request.getDelayMillis());

        // Bước 1: Tạo idempotency key bên Core Banking
        // ⚠️ BUG CỐ Ý: buildCoreIdempotencyKey() gọi strip() để bỏ khoảng trắng
        // Trong khi App layer (IdempotencyServiceImpl) KHÔNG strip externalRef khi tạo key
        // → Nếu externalRef có trailing space (ví dụ CHAR(16) padding),
        //   App key ≠ Core key → App nghĩ là chưa xử lý, Core nghĩ là mới → trừ tiền 2 lần
        String coreIdempotencyKey = buildCoreIdempotencyKey(request.getExternalRef());

        // Bước 2: Kiểm tra duplicate ở tầng Core
        // Nếu key đã tồn tại → Core đã xử lý rồi → bỏ qua (chống trừ tiền lần 2)
        if (coreDebitLogRepository.existsByCoreIdempotencyKey(coreIdempotencyKey)) {

            log.info("Duplicate debit detected by core, skipping");

            return;
        }

        // Bước 3: Thực hiện trừ tiền — UPDATE balance = balance - amount WHERE balance >= amount
        // updatedRows = 1 nghĩa là trừ thành công, = 0 nghĩa là không đủ số dư
        int updatedRows = coreAccountRepository.debit(request.getAccountNo(), request.getAmount());

        if (updatedRows != 1) {throw new IllegalStateException("Core account debit failed");}

        // Bước 4: Ghi log trừ tiền vào CORE_DEBIT_LOG
        // Record này dùng để chống duplicate (bước 2) và đối soát sau này
        CoreDebitLog debitLog = CoreDebitLog.of(
                request.getExternalRef(),
                coreIdempotencyKey,
                request.getAccountNo(),
                request.getAmount()
        );

        coreDebitLogRepository.save(debitLog);

        log.info("Core debit committed successfully");
    }

    private String buildCoreIdempotencyKey(String externalRef) {
        // ⚠️ BUG CỐ Ý: strip() bỏ trailing space khỏi externalRef
        // Ví dụ: externalRef từ CHAR(16) = "TXN001          " (có padding)
        // → strip() → "TXN001" → key = "CORE_DEBIT:TXN001"
        // Nhưng App layer tạo key = "APP_TRANSFER:TXN001          " (giữ nguyên padding)
        // → 2 key khác nhau → idempotency bị phá vỡ giữa 2 tầng
        return "CORE_DEBIT:" + externalRef.strip();
    }

    private void sleep(long delayMillis) {

        if (delayMillis <= 0) {return;}

        try {
            // Giả lập Core Banking xử lý chậm (network latency, DB lock, etc.)
            // Khi delay > readTimeout của client (2000ms) → client nhận ResourceAccessException
            // NHƯNG thread này vẫn tiếp tục chạy → debit vẫn commit thành công trong DB
            // → Client tưởng thất bại, retry → trừ tiền lần 2 = Ghost Double Debit
            Thread.sleep(delayMillis);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException("Core debit was interrupted", exception);
        }
    }
}
