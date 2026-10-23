import {
  render,
  screen,
  waitFor
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

import {
  MemoryRouter
} from "react-router";

import {
  beforeEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import PaymentsPage
  from "./PaymentsPage";

import {
  useAuth
} from "../auth/AuthContext";

import {
  createPayment,
  getPayments
} from "../api/paymentApi";

import {
  getInvoices
} from "../api/invoiceApi";

vi.mock(
  "../auth/AuthContext",
  () => ({
    useAuth:
      vi.fn()
  })
);

vi.mock(
  "../api/paymentApi",
  () => ({
    getPayments:
      vi.fn(),

    createPayment:
      vi.fn()
  })
);

vi.mock(
  "../api/invoiceApi",
  () => ({
    getInvoices:
      vi.fn()
  })
);

describe(
  "PaymentsPage",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();

        useAuth.mockReturnValue({
          token:
            "test-token"
        });

        getPayments.mockResolvedValue([
          {
            paymentId:
              1,

            invoiceId:
              1,

            supplierId:
              1,

            paymentAmount:
              1000,

            status:
              "DRAFT"
          }
        ]);

        getInvoices.mockResolvedValue([
          {
            invoiceId:
              1,

            invoiceNumber:
              "INV-001",

            supplierName:
              "Supplier One",

            invoiceAmount:
              1000
          },

          {
            invoiceId:
              2,

            invoiceNumber:
              "INV-002",

            supplierName:
              "Supplier Two",

            invoiceAmount:
              2500
          }
        ]);
      }
    );

    function renderPage() {

      return render(
        <MemoryRouter>
          <PaymentsPage />
        </MemoryRouter>
      );
    }

    it(
      "loads payments and invoices",
      async () => {

        renderPage();

        expect(
          await screen.findByText(
            "DRAFT"
          )
        ).toBeInTheDocument();

        expect(
          getPayments
        ).toHaveBeenCalledWith(
          "test-token"
        );

        expect(
          getInvoices
        ).toHaveBeenCalledWith(
          "test-token"
        );
      }
    );

    it(
      "hides invoice already used by a payment",
      async () => {

        renderPage();

        await screen.findByText(
          "DRAFT"
        );

        expect(
          screen.queryByRole(
            "option",
            {
              name:
                /INV-001/
            }
          )
        ).not.toBeInTheDocument();

        expect(
          screen.getByRole(
            "option",
            {
              name:
                /INV-002/
            }
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "fills payment amount when invoice is selected",
      async () => {

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "DRAFT"
        );

        await user.selectOptions(
          screen.getByRole(
            "combobox"
          ),
          "2"
        );

        expect(
          screen.getByRole(
            "spinbutton"
          )
        ).toHaveValue(
          2500
        );
      }
    );

    it(
      "creates payment request",
      async () => {

        createPayment.mockResolvedValue({
          paymentId:
            2
        });

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "DRAFT"
        );

        await user.selectOptions(
          screen.getByRole(
            "combobox"
          ),
          "2"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Create Payment"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              createPayment
            ).toHaveBeenCalledWith(
              "test-token",
              "2",
              2500
            );
          }
        );
      }
    );

    it(
      "shows duplicate payment conflict",
      async () => {

        const conflictError =
          new Error(
            "Conflict"
          );

        conflictError.status =
          409;

        createPayment.mockRejectedValue(
          conflictError
        );

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "DRAFT"
        );

        await user.selectOptions(
          screen.getByRole(
            "combobox"
          ),
          "2"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Create Payment"
            }
          )
        );

        expect(
          await screen.findByText(
            "Another user has already created a payment request for this invoice."
          )
        ).toBeInTheDocument();
      }
    );
  }
);