package smartosc.conghung.modules.product.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartosc.conghung.common.exception.AppException;
import smartosc.conghung.common.exception.ErrorCode;
import smartosc.conghung.modules.product.dto.request.ProductRequestDto;
import smartosc.conghung.modules.product.dto.response.ProductResponseDto;
import smartosc.conghung.modules.product.entity.Product;
import smartosc.conghung.modules.product.enums.ProductStatus;
import smartosc.conghung.modules.product.mapper.ProductMapper;
import smartosc.conghung.modules.product.repository.ProductRepository;
import smartosc.conghung.modules.product.service.ProductService;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j(topic = "PRODUCT-SERVICE")
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional
    public ProductResponseDto createProduct(ProductRequestDto request) {
        log.info("Creating new product");

        Product entity = productMapper.toEntity(request);

        entity.setStatus(ProductStatus.ACTIVE.getValue());

        Product saved = productRepository.save(entity);

        log.info("Product created successfully");

        return productMapper.toResponse(saved);
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        log.info("Fetching product by id");

        Product product = productRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        return productMapper.toResponse(product);
    }

    @Override
    public Page<ProductResponseDto> getAllProducts(Pageable pageable) {

        log.info("Fetching all products");

        return productRepository.findAll(pageable).map(productMapper::toResponse);
    }

    @Override
    public List<ProductResponseDto> getProductsByCategory(String category) {

        log.info("Fetching products by category");

        return productRepository.findByCategory(category)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }
}
