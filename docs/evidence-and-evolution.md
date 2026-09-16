# C01 Engineering Spike

## Question / unknown
Bude vedieť náš zvolený stack (Java 21 + Spring Boot 4 + Spring Data JPA + Hibernate) úspešne a bezchybne komunikovať s lokálnym kontajnerom PostgreSQL 16 bežiacim cez Docker, a správne persistovať entity rezervácie?

## What we did
1. Pripravili sme kontajner s PostgreSQL pomocou `docker-compose.yml`.
2. Vytvorili sme čistú štruktúru projektu s požadovanými závislosťami (Spring Web, Data JPA, PostgreSQL Driver, Lombok).
3. Implementovali sme entity `Vehicle`, `Reservation`, `ReservationState` a repozitár `ReservationRepository`.
4. Vytvorili sme `ReservationController` s reálnymi endpointmi (`POST`, `GET`, `PUT`, `DELETE`).
5. Spustili sme aplikáciu a otestovali zápis a čítanie dát do reálnej databázy pomocou PowerShell príkazov (`Invoke-RestMethod`).

## Observed result
Aplikácia sa úspešne pripojila k databáze cez HikariCP pool. HTTP požiadavky (`POST /reservations`, `GET`, `PUT`, `DELETE`) prebehli bez chýb, dáta sa správne uložili do tabuliek v PostgreSQL a aplikácia vracala očakávané odpovede vrátane generovaných ID.

## Decision / what changes because of the result
Engineering Spike A (Persistence) bol úspešne ukončený s pozitívnym výsledkom. Potvrdili sme, že zvolený technologický stack je plne funkčný a pripravený pre ďalší vývoj. Architektúra pre perzistenciu ostáva zachovaná.