@param java.util.List<hexlet.code.model.Url> urls
@param String flash

<!DOCTYPE html>
<html lang="ru">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Сайты</title>
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

      <h1 class="mb-4">Сайты</h1>
      <table class="table table-bordered table-hover bg-white" data-test="urls">
        <thead>
          <tr>
            <th>ID</th>
            <th>Имя</th>
            <th>Последняя проверка</th>
            <th>Код ответа</th>
          </tr>
        </thead>
        <tbody>
          @for(var url : urls)
            <tr>
              <td>${url.getId()}</td>
              <td><a href="/urls/${url.getId()}">${url.getName()}</a></td>
              <td>${url.getLastCheckCreatedAt() != null ? url.getLastCheckCreatedAt().toString() : ""}</td>
              <td>${url.getLastCheckStatusCode() != null ? url.getLastCheckStatusCode().toString() : ""}</td>
            </tr>
          @endfor
        </tbody>
      </table>
    </main>
  </body>
</html>
