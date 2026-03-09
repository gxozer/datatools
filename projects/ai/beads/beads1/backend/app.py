from flask import Flask
from flask_cors import CORS


class HelloWorldApp:
    """Encapsulates the Flask application and its configuration."""

    def __init__(self):
        self.app = Flask(__name__)
        CORS(self.app)
        self._register_routes()

    def _register_routes(self):
        self.app.add_url_rule(
            "/api/hello",
            view_func=self.hello,
            methods=["GET"],
        )

    def hello(self):
        raise NotImplementedError("Implement in beads1-t7b")

    def run(self, **kwargs):
        self.app.run(**kwargs)


hello_world_app = HelloWorldApp()
app = hello_world_app.app

if __name__ == "__main__":
    hello_world_app.run(host="0.0.0.0", port=5000, debug=True)
