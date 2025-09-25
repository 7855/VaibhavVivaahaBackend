package com.uravugal.matrimony.dtos;

import lombok.Data;

@Data
public class PaginationData {
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private int pageSize;
}
