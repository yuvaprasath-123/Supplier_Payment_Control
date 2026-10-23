import {
  useEffect,
  useMemo,
  useState
} from "react";

import {
  createSupplier,
  getSuppliers,
  updateSupplierStatus
} from "../api/supplierApi";

import {
  useAuth
} from "../auth/AuthContext";

import ErrorMessage
  from "../components/ErrorMessage";

export default function SuppliersPage() {

  const {
    token
  } = useAuth();

  const [
    suppliers,
    setSuppliers
  ] = useState([]);

  const [
    supplierName,
    setSupplierName
  ] = useState("");

  const [
    error,
    setError
  ] = useState("");

  const [
    submitting,
    setSubmitting
  ] = useState(false);

  const [
    updatingSupplierId,
    setUpdatingSupplierId
  ] = useState(null);

  const [
    sortDirection,
    setSortDirection
  ] = useState("asc");

  async function loadSuppliers() {

    try {

      setError("");

      const data =
        await getSuppliers(
          token
        );

      setSuppliers(
        data
      );

    } catch (error) {

      setError(
        error.message
      );
    }
  }

  useEffect(
    () => {

      if (token) {

        loadSuppliers();
      }

    },
    [token]
  );

  const sortedSuppliers =
    useMemo(
      () => {

        return [
          ...suppliers
        ].sort(
          (supplierA, supplierB) => {

            const supplierIdA =
              Number(
                supplierA.supplierId
              );

            const supplierIdB =
              Number(
                supplierB.supplierId
              );

            if (
              sortDirection === "asc"
            ) {

              return (
                supplierIdA -
                supplierIdB
              );
            }

            return (
              supplierIdB -
              supplierIdA
            );
          }
        );

      },
      [
        suppliers,
        sortDirection
      ]
    );

  function handleSortById() {

    setSortDirection(
      currentDirection =>
        currentDirection === "asc"
          ? "desc"
          : "asc"
    );
  }

  async function handleCreate(
    event
  ) {

    event.preventDefault();

    if (submitting) {
      return;
    }

    setSubmitting(
      true
    );

    setError("");

    try {

      await createSupplier(
        token,
        supplierName,
        "ACTIVE"
      );

      setSupplierName("");

      await loadSuppliers();

    } catch (error) {

      if (
        error.status === 409
      ) {

        setError(
          "Another user has already created or modified this supplier."
        );

      } else {

        setError(
          error.message
        );
      }

    } finally {

      setSubmitting(
        false
      );
    }
  }

  async function handleStatusChange(
    supplier
  ) {

    if (
      updatingSupplierId !== null
    ) {
      return;
    }

    const newStatus =
      supplier.status === "ACTIVE"
        ? "INACTIVE"
        : "ACTIVE";

    setUpdatingSupplierId(
      supplier.supplierId
    );

    setError("");

    try {

      await updateSupplierStatus(
        token,
        supplier.supplierId,
        newStatus
      );

      await loadSuppliers();

    } catch (error) {

      if (
        error.status === 409
      ) {

        setError(
          "This supplier was modified by another user. Refresh and try again."
        );

        await loadSuppliers();

      } else {

        setError(
          error.message
        );
      }

    } finally {

      setUpdatingSupplierId(
        null
      );
    }
  }

  return (
    <div className="page">

      <h1>
        Suppliers
      </h1>

      <ErrorMessage
        message={
          error
        }
      />

      <form
        className="card form-row"
        onSubmit={
          handleCreate
        }
      >

        <input
          type="text"
          placeholder="Supplier name"
          value={
            supplierName
          }
          onChange={
            event =>
              setSupplierName(
                event.target.value
              )
          }
          required
          disabled={
            submitting
          }
        />

        <button
          type="submit"
          disabled={
            submitting
          }
        >
          {
            submitting
              ? "Creating..."
              : "Create Supplier"
          }
        </button>

      </form>

      <div className="card">

        <table>

          <thead>

            <tr>

              <th>

                <button
                  type="button"
                  onClick={
                    handleSortById
                  }
                  aria-label={
                    sortDirection === "asc"
                      ? "Sort supplier ID descending"
                      : "Sort supplier ID ascending"
                  }
                  className="table-sort-button"
                >
                  ID {
                    sortDirection === "asc"
                      ? "↑"
                      : "↓"
                  }
                </button>

              </th>

              <th>
                Name
              </th>

              <th>
                Status
              </th>

              <th>
                Action
              </th>

            </tr>

          </thead>

          <tbody>

            {
              sortedSuppliers.length === 0
                ? (
                  <tr>

                    <td
                      colSpan="4"
                    >
                      No suppliers available.
                    </td>

                  </tr>
                )
                : (
                  sortedSuppliers.map(
                    supplier => (

                      <tr
                        key={
                          supplier.supplierId
                        }
                      >

                        <td>
                          {
                            supplier.supplierId
                          }
                        </td>

                        <td>
                          {
                            supplier.supplierName
                          }
                        </td>

                        <td>
                          {
                            supplier.status
                          }
                        </td>

                        <td>

                          <button
                            type="button"
                            onClick={
                              () =>
                                handleStatusChange(
                                  supplier
                                )
                            }
                            disabled={
                              updatingSupplierId ===
                              supplier.supplierId
                            }
                          >
                            {
                              updatingSupplierId ===
                              supplier.supplierId
                                ? "Updating..."
                                : supplier.status ===
                                  "ACTIVE"
                                  ? "Deactivate"
                                  : "Activate"
                            }
                          </button>

                        </td>

                      </tr>
                    )
                  )
                )
            }

          </tbody>

        </table>

      </div>

    </div>
  );
}