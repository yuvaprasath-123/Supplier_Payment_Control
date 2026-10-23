import {
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

import SuppliersPage
  from "./SuppliersPage";

import {
  useAuth
} from "../auth/AuthContext";

import {
  createSupplier,
  getSuppliers,
  updateSupplierStatus
} from "../api/supplierApi";

vi.mock(
  "../auth/AuthContext",
  () => ({
    useAuth:
      vi.fn()
  })
);

vi.mock(
  "../api/supplierApi",
  () => ({
    getSuppliers:
      vi.fn(),
    createSupplier:
      vi.fn(),
    updateSupplierStatus:
      vi.fn()
  })
);

describe(
  "SuppliersPage",
  () => {

    beforeEach(
      () => {

        vi.clearAllMocks();

        useAuth.mockReturnValue({
          token:
            "test-token"
        });

        getSuppliers.mockResolvedValue([
          {
            supplierId:
              1,
            supplierName:
              "Supplier One",
            status:
              "ACTIVE"
          }
        ]);
      }
    );

    it(
      "loads suppliers using JWT token",
      async () => {

        render(
          <SuppliersPage />
        );

        expect(
          await screen.findByText(
            "Supplier One"
          )
        ).toBeInTheDocument();

        expect(
          getSuppliers
        ).toHaveBeenCalledWith(
          "test-token"
        );
      }
    );

    it(
      "creates a supplier",
      async () => {

        createSupplier.mockResolvedValue({
          supplierId:
            2,
          supplierName:
            "New Supplier",
          status:
            "ACTIVE"
        });

        const user =
          userEvent.setup();

        render(
          <SuppliersPage />
        );

        await screen.findByText(
          "Supplier One"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Supplier name"
          ),
          "New Supplier"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Create Supplier"
            }
          )
        );

        await waitFor(
          () => {

            expect(
              createSupplier
            ).toHaveBeenCalledWith(
              "test-token",
              "New Supplier",
              "ACTIVE"
            );
          }
        );
      }
    );

    it(
      "changes supplier status",
      async () => {

        updateSupplierStatus
          .mockResolvedValue({
            supplierId:
              1,
            supplierName:
              "Supplier One",
            status:
              "INACTIVE"
          });

        const user =
          userEvent.setup();

        render(
          <SuppliersPage />
        );

        await screen.findByText(
          "Supplier One"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Deactivate"
            }
          )
        );

        expect(
          updateSupplierStatus
        ).toHaveBeenCalledWith(
          "test-token",
          1,
          "INACTIVE"
        );
      }
    );

    it(
      "shows conflict error",
      async () => {

        const error =
          new Error(
            "Conflict"
          );

        error.status =
          409;

        createSupplier.mockRejectedValue(
          error
        );

        const user =
          userEvent.setup();

        render(
          <SuppliersPage />
        );

        await screen.findByText(
          "Supplier One"
        );

        await user.type(
          screen.getByPlaceholderText(
            "Supplier name"
          ),
          "Supplier One"
        );

        await user.click(
          screen.getByRole(
            "button",
            {
              name:
                "Create Supplier"
            }
          )
        );

        expect(
          await screen.findByText(
            /already created or modified/i
          )
        ).toBeInTheDocument();
      }
    );
  }
);