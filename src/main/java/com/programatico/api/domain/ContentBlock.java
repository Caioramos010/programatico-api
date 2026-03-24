package com.programatico.api.domain;

import com.programatico.api.domain.enums.LayoutType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "content_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "module_id", nullable = false)
    private Modulo modulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LayoutType layoutType;

    @Column(columnDefinition = "TEXT")
    private String textContent;

    @Column(length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private Integer displayOrder;
}
