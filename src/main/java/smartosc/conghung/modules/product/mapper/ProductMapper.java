package smartosc.conghung.modules.product.mapper;

import org.mapstruct.Mapper;
import smartosc.conghung.modules.product.dto.request.ProductRequestDto;
import smartosc.conghung.modules.product.dto.response.ProductResponseDto;
import smartosc.conghung.modules.product.entity.Product;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    Product toEntity(ProductRequestDto dto);

    ProductResponseDto toResponse(Product entity);
}
