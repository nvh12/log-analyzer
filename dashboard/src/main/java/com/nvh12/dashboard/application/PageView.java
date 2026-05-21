package com.nvh12.dashboard.application;

import java.util.List;

public record PageView<T>(List<T> content, int page, int size, long total) {}
