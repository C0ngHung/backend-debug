package smartosc.conghung.modules.fee.service.impl;

import org.springframework.stereotype.Service;
import smartosc.conghung.modules.fee.dto.request.FeeRequestDto;
import smartosc.conghung.modules.fee.service.FeeBatchGeneratorService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class FeeBatchGeneratorServiceImpl implements FeeBatchGeneratorService {

    private static final BigDecimal[] NORMAL_AMOUNTS = {
            new BigDecimal("200000"),
            new BigDecimal("500000"),
            new BigDecimal("750000"),
            new BigDecimal("1500000"),
            new BigDecimal("2000000"),
            new BigDecimal("3000000"),
            new BigDecimal("5000000"),
            new BigDecimal("8000000"),
            new BigDecimal("10000000"),
            new BigDecimal("15000000"),
            new BigDecimal("25000000"),
            new BigDecimal("50000000"),
            new BigDecimal("75000000"),
            new BigDecimal("100000000")
    };

    private static final BigDecimal BOMB_AMOUNT_1 = new BigDecimal("5000000.00");
    private static final BigDecimal BOMB_AMOUNT_2 = new BigDecimal("500000.0");
    private static final BigDecimal BOMB_AMOUNT_3 = new BigDecimal("3000000.00");
    private static final BigDecimal BOMB_AMOUNT_4 = new BigDecimal("1000000.00");
    private static final BigDecimal BOMB_AMOUNT_5 = new BigDecimal("10000000.0");

    private static final int TOTAL_BATCH_SIZE = 1000;
    private static final int TOTAL_ACCOUNTS = 50;

    @Override
    public List<FeeRequestDto> generateBatch() {
        List<FeeRequestDto> batch = new ArrayList<>(TOTAL_BATCH_SIZE);
        Random random = new Random(42);

        for (int i = 1; i <= TOTAL_BATCH_SIZE; i++) {
            String transactionId = String.format("TXN%04d", i);
            String accountNumber = String.format("ACC%03d", (i % TOTAL_ACCOUNTS) + 1);
            BigDecimal amount = selectAmount(i, random);

            batch.add(new FeeRequestDto(transactionId, accountNumber, amount));
        }

        return batch;
    }

    private BigDecimal selectAmount(int index, Random random) {
        return switch (index) {
            case 237 -> BOMB_AMOUNT_1;
            case 512 -> BOMB_AMOUNT_2;
            case 789 -> BOMB_AMOUNT_3;
            case 156 -> BOMB_AMOUNT_4;
            case 934 -> BOMB_AMOUNT_5;
            default -> NORMAL_AMOUNTS[random.nextInt(NORMAL_AMOUNTS.length)];
        };
    }
}
