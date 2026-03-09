from flask import Flask, jsonify
from flask_cors import CORS

app = Flask(__name__)

# CORS (Cross-Origin Resource Sharing) allows the React frontend (port 5173)
# to make requests to this backend (port 5000). Without it, the browser would
# block these requests as they come from a different origin.
CORS(app)


@app.route("/api/hello")
def hello():
    return jsonify({"message": "Hello, World!"})


if __name__ == "__main__":
    app.run(port=5000, debug=True)
