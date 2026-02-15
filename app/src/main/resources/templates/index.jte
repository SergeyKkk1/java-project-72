<!DOCTYPE html>
<html lang="ru">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Анализатор страниц</title>
    <link
      href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
      rel="stylesheet"
    >
    <style>
      :root {
        --hero-bg: #212529;
        --hero-border: #2b3035;
      }
      body {
        background-color: #f8f9fa;
      }
      .hero {
        background-color: var(--hero-bg);
        border-bottom: 1px solid var(--hero-border);
      }
      .hero .form-text {
        color: #9aa0a6;
      }
    </style>
  </head>
  <body>
    <header class="hero text-light">
      <nav class="navbar navbar-expand-sm navbar-dark">
        <div class="container">
          <a class="navbar-brand fw-semibold" href="/">Анализатор страниц</a>
          <div class="navbar-nav gap-2">
            <a class="nav-link active" href="/">Главная</a>
            <a class="nav-link" href="/urls">Сайты</a>
          </div>
        </div>
      </nav>

      <section class="py-5">
        <div class="container">
          <h1 class="display-5 fw-semibold mb-2">Анализатор страниц</h1>
          <p class="lead text-secondary mb-4">Бесплатно проверяйте сайты на SEO пригодность</p>
          <form action="/urls" method="post">
            <div class="row g-2 align-items-center">
              <div class="col-md-9">
                <input
                  type="url"
                  name="url"
                  class="form-control form-control-lg"
                  placeholder="Ссылка"
                  aria-label="Ссылка"
                  required
                >
              </div>
              <div class="col-md-3 d-grid">
                <button type="submit" class="btn btn-primary btn-lg">Проверить</button>
              </div>
            </div>
            <div class="form-text mt-2">Пример: https://www.example.com</div>
          </form>
        </div>
      </section>
    </header>

    <main class="bg-white">
      <div class="container py-5"></div>
    </main>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
  </body>
</html>
