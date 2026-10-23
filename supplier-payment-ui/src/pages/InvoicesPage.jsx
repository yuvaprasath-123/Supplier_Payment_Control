import {
  useEffect,
  useMemo,
  useState
} from "react";

import {
  createInvoice,
  getInvoices
} from "../api/invoiceApi";

import {
  getSuppliers
} from "../api/supplierApi";

import {
  useAuth
} from "../auth/AuthContext";

import ErrorMessage
  from "../components/ErrorMessage";

export default function InvoicesPage() {

  const {
    token
  } = useAuth();

  const [
    invoices,
    setInvoices
  ] = useState([]);

  const [
    suppliers,
    setSuppliers
  ] = useState([]);

  const [
    supplierId,
    setSupplierId
  ] = useState("");

  const [
    invoiceNumber,
    setInvoiceNumber
  ] = useState("");

  const [
    invoiceAmount,
    setInvoiceAmount
  ] = useState("");

  const [
    invoiceDate,
    setInvoiceDate
  ] = useState("");

  const [
    dueDate,
    setDueDate
  ] = useState("");

  const [
    error,
    setError
  ] = useState("");

  const [
    loading,
    setLoading
  ] = useState(true);

  const [
    submitting,
    setSubmitting
  ] = useState(false);

  const [
    sortDirection,
    setSortDirection
  ] = useState("asc");

  async function loadData() {

    try {

      setError("");

      const [
        invoiceData,
        supplierData
      ] =
        await Promise.all([
          getInvoices(
            token
          ),

          getSuppliers(
            token
          )
        ]);

      setInvoices(
        invoiceData
      );

      setSuppliers(
        supplierData
      );

    } catch (error) {

      setError(
        error.message
      );

    } finally {

      setLoading(false);
    }
  }

  useEffect(
    () => {

      if (token) {

        loadData();
      }

    },
    [token]
  );

  const sortedInvoices =
    useMemo(
      () => {

        return [
          ...invoices
        ].sort(
          (invoiceA, invoiceB) => {

            const invoiceIdA =
              Number(
                invoiceA.invoiceId
              );

            const invoiceIdB =
              Number(
                invoiceB.invoiceId
              );

            if (
              sortDirection === "asc"
            ) {

              return (
                invoiceIdA -
                invoiceIdB
              );
            }

            return (
              invoiceIdB -
              invoiceIdA
            );
          }
        );

      },
      [
        invoices,
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

    setError("");

    if (
      dueDate &&
      invoiceDate &&
      dueDate < invoiceDate
    ) {

      setError(
        "Due date cannot be earlier than invoice date."
      );

      return;
    }

    if (
      Number(
        invoiceAmount
      ) <= 0
    ) {

      setError(
        "Invoice amount must be greater than zero."
      );

      return;
    }

    setSubmitting(true);

    try {

      const request = {

        supplierId:
          Number(
            supplierId
          ),

        invoiceNumber:
          invoiceNumber.trim(),

        invoiceAmount:
          Number(
            invoiceAmount
          ),

        invoiceDate,

        dueDate
      };

      await createInvoice(
        token,
        request
      );

      setSupplierId("");
      setInvoiceNumber("");
      setInvoiceAmount("");
      setInvoiceDate("");
      setDueDate("");

      await loadData();

    } catch (error) {

      if (
        error.status === 409
      ) {

        await loadData();

        setError(
          "An invoice with this invoice number already exists for the selected supplier."
        );

        return;
      }

      setError(
        error.message
      );

    } finally {

      setSubmitting(false);
    }
  }

  if (loading) {

    return (
      <div className="page">

        <p>
          Loading invoices...
        </p>

      </div>
    );
  }

  return (
    <div className="page">

      <h1>
        Invoices
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

        <select
          value={
            supplierId
          }
          onChange={
            event =>
              setSupplierId(
                event.target.value
              )
          }
          required
          disabled={
            submitting
          }
        >

          <option value="">
            Select supplier
          </option>

          {
            suppliers
              .filter(
                supplier =>
                  supplier.status ===
                  "ACTIVE"
              )
              .map(
                supplier => (

                  <option
                    key={
                      supplier.supplierId
                    }
                    value={
                      supplier.supplierId
                    }
                  >
                    {
                      supplier.supplierName
                    }
                  </option>
                )
              )
          }

        </select>

        <input
          type="text"
          placeholder="Invoice number"
          value={
            invoiceNumber
          }
          onChange={
            event =>
              setInvoiceNumber(
                event.target.value
              )
          }
          required
          disabled={
            submitting
          }
        />

        <input
          type="number"
          placeholder="Invoice amount"
          min="0.01"
          step="0.01"
          value={
            invoiceAmount
          }
          onChange={
            event =>
              setInvoiceAmount(
                event.target.value
              )
          }
          required
          disabled={
            submitting
          }
        />

        <input
          type="date"
          value={
            invoiceDate
          }
          onChange={
            event =>
              setInvoiceDate(
                event.target.value
              )
          }
          required
          disabled={
            submitting
          }
        />

        <input
          type="date"
          min={
            invoiceDate ||
            undefined
          }
          value={
            dueDate
          }
          onChange={
            event =>
              setDueDate(
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
              : "Create Invoice"
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
                      ? "Sort invoice ID descending"
                      : "Sort invoice ID ascending"
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
                Invoice Number
              </th>

              <th>
                Supplier
              </th>

              <th>
                Amount
              </th>

              <th>
                Invoice Date
              </th>

              <th>
                Due Date
              </th>

            </tr>

          </thead>

          <tbody>

            {
              sortedInvoices.length === 0
                ? (
                  <tr>

                    <td
                      colSpan="6"
                    >
                      No invoices available.
                    </td>

                  </tr>
                )
                : (
                  sortedInvoices.map(
                    invoice => {

                      const supplier =
                        suppliers.find(
                          item =>
                            item.supplierId ===
                            invoice.supplierId
                        );

                      return (
                        <tr
                          key={
                            invoice.invoiceId
                          }
                        >

                          <td>
                            {
                              invoice.invoiceId
                            }
                          </td>

                          <td>
                            {
                              invoice.invoiceNumber
                            }
                          </td>

                          <td>
                            {
                              invoice.supplierName ||
                              supplier?.supplierName ||
                              invoice.supplierId
                            }
                          </td>

                          <td>
                            {
                              invoice.invoiceAmount
                            }
                          </td>

                          <td>
                            {
                              invoice.invoiceDate
                            }
                          </td>

                          <td>
                            {
                              invoice.dueDate
                            }
                          </td>

                        </tr>
                      );
                    }
                  )
                )
            }

          </tbody>

        </table>

      </div>

    </div>
  );
}