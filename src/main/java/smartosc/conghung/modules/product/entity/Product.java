package smartosc.conghung.modules.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "TBL_PRODUCT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tbl_product_id_gen")
    @SequenceGenerator(name = "tbl_product_id_gen", sequenceName = "TBL_PRODUCT_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "u_product_name", nullable = false)
    private String uProductName;

    @Column(name = "u_description", length = 500)
    private String uDescription;

    @Column(name = "u_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal uPrice;

    @Column(name = "category", length = 100)
    private String category;

    @Column(nullable = false, length = 20)
    @ColumnDefault("'ACTIVE'")
    private String status;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
