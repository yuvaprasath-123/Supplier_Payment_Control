package com.ofss.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SupplierInvoiceClient {

    private final RestClient restClient;


    public SupplierInvoiceClient(
            @Value(
                "${supplier-invoice-service.base-url:"
                + "http://localhost:8082}"
            )
            String baseUrl
    ) {

        this.restClient =
                RestClient
                        .builder()
                        .baseUrl(
                                baseUrl
                        )
                        .build();
    }


    /*
     * =========================================================
     * GET INVOICE
     * =========================================================
     *
     * Reads the authoritative invoice from
     * supplier-invoice-service.
     *
     * The caller's bearer token is forwarded because
     * supplier-invoice-service is protected.
     */
    public InvoiceClientResponse getInvoice(
            Long invoiceId,
            String authorization
    ) {

        if (
                invoiceId == null
                ||
                invoiceId <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid invoice ID required"
            );
        }


        if (
                authorization == null
                ||
                !authorization.startsWith(
                        "Bearer "
                )
                ||
                authorization
                        .substring(7)
                        .isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authorization token required "
                            + "to retrieve invoice"
            );
        }


        try {

            InvoiceClientResponse response =
                    restClient
                            .get()
                            .uri(
                                    "/api/v1/invoices/{invoiceId}",
                                    invoiceId
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    authorization
                            )
                            .retrieve()
                            .body(
                                    InvoiceClientResponse.class
                            );


            if (response == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Supplier invoice service "
                                + "returned an empty response"
                );
            }


            if (
                    response.invoiceId() == null
                    ||
                    !invoiceId.equals(
                            response.invoiceId()
                    )
            ) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Supplier invoice service returned "
                                + "an unexpected invoice"
                );
            }


            return response;


        } catch (
                ResponseStatusException ex
        ) {

            throw ex;


        } catch (
                RestClientResponseException ex
        ) {

            if (
                    ex.getStatusCode()
                            .value()
                            == 404
            ) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Invoice "
                                + invoiceId
                                + " was not found in "
                                + "supplier-invoice-service",
                        ex
                );
            }


            if (
                    ex.getStatusCode()
                            .value()
                            == 401
                    ||
                    ex.getStatusCode()
                            .value()
                            == 403
            ) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Supplier invoice service "
                                + "rejected the service request",
                        ex
                );
            }


            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to retrieve invoice "
                            + "from supplier-invoice-service",
                    ex
            );


        } catch (
                RestClientException ex
        ) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Supplier invoice service "
                            + "is unavailable",
                    ex
            );
        }
    }
}