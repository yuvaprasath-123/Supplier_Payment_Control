import {
  fireEvent,
  render,
  screen,
  waitFor
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

import {
  beforeEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import InvoicesPage
  from "./InvoicesPage";

import {
  useAuth
} from "../auth/AuthContext";

import {
  createInvoice,
  getInvoices
} from "../api/invoiceApi";

import {
  getSuppliers
} from "../api/supplierApi";

vi.mock(
  "../auth/AuthContext",
  () => ({
    useAuth:
      vi.fn()
  })
);

vi.mock(
  "../api/invoiceApi",
  () => ({
    getInvoices:
      vi.fn(),

    createInvoice:
      vi.fn()
  })
);

vi.mock(
  "../api/supplierApi",
  () => ({
    getSuppliers:
      vi.fn()
  })
);

describe(
  "InvoicesPage",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();

        useAuth.mockReturnValue({
          token:
            "test-token"
        });

        getInvoices.mockResolvedValue([
          {
            invoiceId:
              1,

            supplierId:
              1,

            invoiceNumber:
              "INV-001",

            invoiceAmount:
              1000,

            invoiceDate:
              "2026-10-01",

            dueDate:
              "2026-10-20"
          }
        ]);

        getSuppliers.mockResolvedValue([
          {
            supplierId:
              1,

            supplierName:
              "Supplier One",

            status:
              "ACTIVE"
          },

          {
            supplierId:
              2,

            supplierName:
              "Inactive Supplier",

            status:
              "INACTIVE"
          }
        ]);
      }
    );

    it(
      "loads invoices and suppliers with JWT",
      async () => {

        render(
          <InvoicesPage />
        );

        expect(
          await screen.findByText(
            "INV-001"
          )
        ).toBeInTheDocument();

        expect(
          getInvoices
        ).toHaveBeenCalledWith(
          "test-token"
        );

        expect(
          getSuppliers
        ).toHaveBeenCalledWith(
          "test-token"
        );
      }
    );

    it(
      "shows only active suppliers in dropdown",
      async () => {

        render(
          <InvoicesPage />
        );

        await screen.findByText(
          "INV-001"
        );

        expect(
          screen.getByRole(
            "option",
            {
              name:
                "Supplier One"
            }
          )
        ).toBeInTheDocument();

        expect(
          screen.queryByRole(
            "option",
            {
              name:
                "Inactive Supplier"
            }
          )
        ).not.toBeInTheDocument();
      }
    );

    it(
      "creates an invoice",
      async () => {

        createInvoice.mockResolvedValue({
          invoiceId:
            2
        });

        const user =
          userEvent.setup();

        render(
          <InvoicesPage />
        );

        await screen.findByText(
          "INV-001"
        );

        await user.selectOptions(
          screen.getByRole(
            "combobox"
          ),
          "1"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Invoice number"
          ),
          "INV-002"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Invoice amount"
          ),
          "2500"
        );

        const dateInputs =
          document.querySelectorAll(
            'input[type="date"]'
          );

        fireEvent.change(
          dateInputs[0],
          {
            target: {
              value:
                "2026-10-10"
            }
          }
        );

        fireEvent.change(
          dateInputs[1],
          {
            target: {
              value:
                "2026-10-20"
            }
          }
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Create Invoice"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              createInvoice
            ).toHaveBeenCalledWith(
              "test-token",
              {
                supplierId:
                  1,

                invoiceNumber:
                  "INV-002",

                invoiceAmount:
                  2500,

                invoiceDate:
                  "2026-10-10",

                dueDate:
                  "2026-10-20"
              }
            );
          }
        );
      }
    );

    it(
      "blocks invalid due date",
      async () => {

        const user =
          userEvent.setup();

        render(
          <InvoicesPage />
        );

        await screen.findByText(
          "INV-001"
        );

        await user.selectOptions(
          screen.getByRole(
            "combobox"
          ),
          "1"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Invoice number"
          ),
          "INV-003"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Invoice amount"
          ),
          "100"
        );

        const dateInputs =
          document.querySelectorAll(
            'input[type="date"]'
          );

        fireEvent.change(
          dateInputs[0],
          {
            target: {
              value:
                "2026-10-20"
            }
          }
        );

        fireEvent.change(
          dateInputs[1],
          {
            target: {
              value:
                "2026-10-10"
            }
          }
        );

        /*
         * The due-date input has:
         *
         * min="2026-10-20"
         *
         * Normal browser form submission therefore
         * stops before React's submit handler runs.
         *
         * fireEvent.submit deliberately exercises
         * our application validation.
         */
        const form =
          screen
            .getByRole(
              "button",
              {
                name:
                  "Create Invoice"
              }
            )
            .closest(
              "form"
            );

        fireEvent.submit(
          form
        );

        expect(
          await screen.findByText(
            "Due date cannot be earlier than invoice date."
          )
        ).toBeInTheDocument();

        expect(
          createInvoice
        ).not.toHaveBeenCalled();
      }
    );
  }
);