package smartosc.conghung.modules.product.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import smartosc.conghung.modules.product.dto.request.ProductRequestDto;
import smartosc.conghung.modules.product.dto.response.ProductResponseDto;

import java.util.List;

public interface ProductService {

    ProductResponseDto createProduct(ProductRequestDto request);

    ProductResponseDto getProductById(Long id);

    Page<ProductResponseDto> getAllProducts(Pageable pageable);

    List<ProductResponseDto> getProductsByCategory(String category);
}
