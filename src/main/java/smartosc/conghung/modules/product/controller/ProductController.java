package smartosc.conghung.modules.product.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartosc.conghung.common.constant.ApiConstant;
import smartosc.conghung.common.response.ApiResult;
import smartosc.conghung.modules.product.dto.request.ProductRequestDto;
import smartosc.conghung.modules.product.dto.response.ProductResponseDto;
import smartosc.conghung.modules.product.service.ProductService;

import java.util.List;

@RestController
@RequestMapping(ApiConstant.ApiProduct.BASE)
@Tag(name = "Product Controller", description = "APIs for product management")
@Slf4j(topic = "PRODUCT-CONTROLLER")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create product", description = "Creates a new product")
    @ApiResponse(responseCode = "201", description = "Product created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request data")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @PostMapping
    public ResponseEntity<ApiResult<ProductResponseDto>> createProduct(
            @Valid @RequestBody ProductRequestDto request) {

        ProductResponseDto response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResult.success("Product created", response));
    }

    @Operation(summary = "Get product by ID", description = "Returns a single product by its ID")
    @ApiResponse(responseCode = "200", description = "Product retrieved successfully")
    @ApiResponse(responseCode = "404", description = "Product not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping(ApiConstant.ApiProduct.GET_BY_ID)
    public ResponseEntity<ApiResult<ProductResponseDto>> getProductById(
            @PathVariable Long id) {

        ProductResponseDto response = productService.getProductById(id);

        return ResponseEntity.ok(ApiResult.success(response));
    }

    @Operation(summary = "Get all products", description = "Returns paginated products")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping
    public ResponseEntity<ApiResult<Page<ProductResponseDto>>> getAllProducts(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {

        Page<ProductResponseDto> response = productService.getAllProducts(pageable);

        return ResponseEntity.ok(ApiResult.success(response));
    }

    @Operation(summary = "Get products by category", description = "Returns products filtered by category")
    @ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping(ApiConstant.ApiProduct.GET_BY_CATEGORY)
    public ResponseEntity<ApiResult<List<ProductResponseDto>>> getProductsByCategory(
            @PathVariable String category) {

        List<ProductResponseDto> response = productService.getProductsByCategory(category);

        return ResponseEntity.ok(ApiResult.success(response));
    }
}

