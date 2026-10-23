export async function apiRequest(
  url,
  {
    token,
    method = "GET",
    body
  } = {}
) {

  const headers = {};

  if (body !== undefined) {

    headers["Content-Type"] =
      "application/json";
  }

  if (token) {

    headers.Authorization =
      `Bearer ${token}`;
  }

  const response =
    await fetch(
      url,
      {
        method,
        headers,

        body:
          body !== undefined
            ? JSON.stringify(
                body
              )
            : undefined
      }
    );

  let data = null;

  const contentType =
    response.headers.get(
      "content-type"
    );

  if (
    contentType &&
    contentType.includes(
      "application/json"
    )
  ) {

    try {

      data =
        await response.json();

    } catch {

      data = null;
    }
  }

  if (!response.ok) {

    const error =
      new Error(
        data?.message ||
        `Request failed with status ${response.status}`
      );

    error.status =
      response.status;

    error.data =
      data;

    throw error;
  }

  return data;
}