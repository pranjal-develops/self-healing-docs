package com.docdebt.dto;

public record HealResultDto(
        String moduleName,
        DocHealResult technical,
        DocHealResult business,
        int summariesFolded
) {
    public record DocHealResult(
            String oldContent,
            String newContent,
            String draftPath,
            boolean wasScaffolded
    ) {}
}
