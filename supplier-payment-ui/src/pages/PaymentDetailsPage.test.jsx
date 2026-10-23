import {
  render,
  screen,
  waitFor
} from "@testing-library/react";

import userEvent
  from "@testing-library/user-event";

import {
  MemoryRouter,
  Route,
  Routes
} from "react-router";

import {
  beforeEach,
  describe,
  expect,
  it,
  vi
} from "vitest";

import PaymentDetailsPage
  from "./PaymentDetailsPage";

import {
  useAuth
} from "../auth/AuthContext";

import {
  getPayment,
  getPaymentTimeline,
  submitPayment,
  updatePayment
} from "../api/paymentApi";

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
    getPayment:
      vi.fn(),

    getPaymentTimeline:
      vi.fn(),

    submitPayment:
      vi.fn(),

    updatePayment:
      vi.fn()
  })
);

describe(
  "PaymentDetailsPage",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();

        useAuth.mockReturnValue({
          token:
            "test-token"
        });

        getPayment.mockResolvedValue({
          paymentId:
            1,

          invoiceId:
            10,

          supplierId:
            20,

          requesterId:
            100,

          paymentAmount:
            5000,

          status:
            "DRAFT",

          riskScore:
            null,

          riskBand:
            null,

          requiredApprovalLevel:
            null
        });

        getPaymentTimeline.mockResolvedValue([
          {
            id:
              1,

            previousStatus:
              null,

            newStatus:
              "DRAFT",

            changedBy:
              100,

            changedAt:
              "2026-10-13T10:00:00",

            reason:
              "Created"
          }
        ]);
      }
    );

    function renderPage() {

      return render(
        <MemoryRouter
          initialEntries={[
            "/payments/1"
          ]}
        >

          <Routes>

            <Route
              path="/payments/:paymentId"
              element={
                <PaymentDetailsPage />
              }
            />

          </Routes>

        </MemoryRouter>
      );
    }

    it(
      "loads payment details and timeline",
      async () => {

        renderPage();

        expect(
          await screen.findByText(
            "Payment Details"
          )
        ).toBeInTheDocument();

        expect(
          getPayment
        ).toHaveBeenCalledWith(
          "test-token",
          "1"
        );

        expect(
          getPaymentTimeline
        ).toHaveBeenCalledWith(
          "test-token",
          "1"
        );

        expect(
          screen.getByText(
            "Created"
          )
        ).toBeInTheDocument();
      }
    );

    it(
      "updates draft payment",
      async () => {

        updatePayment.mockResolvedValue({
          paymentId:
            1
        });

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "Payment Details"
        );

        const amountInput =
          screen.getByRole(
            "spinbutton"
          );

        await user.clear(
          amountInput
        );

        await user.type(
          amountInput,
          "6000"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Update Payment"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              updatePayment
            ).toHaveBeenCalledWith(
              "test-token",
              "1",
              "6000"
            );
          }
        );
      }
    );

    it(
      "submits draft payment",
      async () => {

        submitPayment.mockResolvedValue({
          paymentId:
            1,

          status:
            "PENDING_VALIDATION"
        });

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "Payment Details"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Submit Payment"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              submitPayment
            ).toHaveBeenCalledWith(
              "test-token",
              "1"
            );
          }
        );
      }
    );

    it(
      "shows optimistic locking conflict",
      async () => {

        const conflictError =
          new Error(
            "Conflict"
          );

        conflictError.status =
          409;

        updatePayment.mockRejectedValue(
          conflictError
        );

        const user =
          userEvent.setup();

        renderPage();

        await screen.findByText(
          "Payment Details"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Update Payment"
            }
          )
        );

        expect(
          await screen.findByText(
            "This payment request was modified by another user. Refresh and try again."
          )
        ).toBeInTheDocument();
      }
    );
  }
);