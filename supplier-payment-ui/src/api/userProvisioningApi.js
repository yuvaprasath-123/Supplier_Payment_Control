export async function createRequester(
  token,
  requester
) {

  const response =
    await fetch(
      "/security-api/api/v1/user-provisioning/requesters",
      {
        method: "POST",

        headers: {
          "Content-Type":
            "application/json",

          Authorization:
            `Bearer ${token}`
        },

        body:
          JSON.stringify(
            requester
          )
      }
    );

  const data =
    await response.json();

  if (!response.ok) {

    const error =
      new Error(
        data.message ||
        "Unable to create requester account."
      );

    error.status =
      response.status;

    throw error;
  }

  return data;
}