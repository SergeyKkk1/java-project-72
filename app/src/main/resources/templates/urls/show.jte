@param hexlet.code.model.Url url
@param String flash

<!DOCTYPE html>
<html lang="ru">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Сайт ${url.getName()}</title>
    <link
      href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
      rel="stylesheet"
    >
  </head>
  <body class="bg-light">
    <nav class="navbar navbar-expand-sm navbar-dark bg-dark">
      <div class="container">
        <a class="navbar-brand fw-semibold" href="/">Анализатор страниц</a>
        <div class="navbar-nav gap-2">
          <a class="nav-link" href="/">Главная</a>
          <a class="nav-link active" href="/urls">Сайты</a>
        </div>
      </div>
    </nav>

    <main class="container py-4">
      @if(flash != null)
        <div class="alert alert-info" role="alert">${flash}</div>
      @endif

      <h1 class="mb-4">Сайт: ${url.getName()}</h1>
      <table class="table table-bordered bg-white">
        <tbody>
          <tr>
            <th scope="row">ID</th>
            <td>${url.getId()}</td>
          </tr>
          <tr>
            <th scope="row">Имя</th>
            <td>${url.getName()}</td>
          </tr>
          <tr>
            <th scope="row">Дата создания</th>
            <td>${url.getCreatedAt() != null ? url.getCreatedAt().toString() : ""}</td>
          </tr>
        </tbody>
      </table>
    </main>
  </body>
</html>
