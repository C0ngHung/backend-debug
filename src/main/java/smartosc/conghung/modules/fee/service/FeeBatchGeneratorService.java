package smartosc.conghung.modules.fee.service;

import smartosc.conghung.modules.fee.dto.request.FeeRequestDto;

import java.util.List;

public interface FeeBatchGeneratorService {

    List<FeeRequestDto> generateBatch();
}
