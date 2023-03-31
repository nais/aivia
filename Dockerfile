FROM ghcr.io/navikt/baseimages/temurin:19

COPY build/libs/*.jar ./
