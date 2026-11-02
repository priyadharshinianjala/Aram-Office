package com.aramoffice.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Profile("render")
public class RenderDemoPdfSeeder implements ApplicationRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RenderDemoPdfSeeder.class);

    private static final List<DemoPdf> DEMO_PDFS = List.of(
            new DemoPdf("DPDF01", "person-details-nandhini.pdf",
                    "demo-pdfs/person-details-c003.pdf"),
            new DemoPdf("DPDF02", "service-profile-nandhini.pdf",
                    "demo-pdfs/service-profile-c003.pdf"),
            new DemoPdf("DPDF03", "person-details-meenakshi.pdf",
                    "demo-pdfs/person-details-c004.pdf"),
            new DemoPdf("DPDF04", "service-profile-meenakshi.pdf",
                    "demo-pdfs/service-profile-c004.pdf"),
            new DemoPdf("DPDF05", "person-details-kavitha.pdf",
                    "demo-pdfs/person-details-c005.pdf"),
            new DemoPdf("DPDF06", "service-profile-kavitha.pdf",
                    "demo-pdfs/service-profile-c005.pdf"),
            new DemoPdf("DPDF07", "person-details-divya.pdf",
                    "demo-pdfs/person-details-c006.pdf"),
            new DemoPdf("DPDF08", "service-profile-divya.pdf",
                    "demo-pdfs/service-profile-c006.pdf"),
            new DemoPdf("DPDF09", "person-details-ananya.pdf",
                    "demo-pdfs/person-details-c007.pdf"),
            new DemoPdf("DPDF10", "service-profile-ananya.pdf",
                    "demo-pdfs/service-profile-c007.pdf"));

    private final DocumentFileService documentFileService;

    public RenderDemoPdfSeeder(DocumentFileService documentFileService) {
        this.documentFileService = documentFileService;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (DemoPdf demoPdf : DEMO_PDFS) {
            ClassPathResource resource =
                    new ClassPathResource(demoPdf.resourcePath());
            try (var input = resource.getInputStream()) {
                documentFileService.restoreBundledPdf(
                        demoPdf.documentId(),
                        demoPdf.originalFilename(),
                        input.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Unable to load a bundled demo PDF.", exception);
            }
        }
        LOGGER.info("Restored {} bundled synthetic demo PDFs.", DEMO_PDFS.size());
    }

    private record DemoPdf(
            String documentId,
            String originalFilename,
            String resourcePath) {
    }
}
