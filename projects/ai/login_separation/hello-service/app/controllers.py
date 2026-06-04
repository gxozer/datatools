from flask import g, jsonify


class HelloController:
    @staticmethod
    def hello():
        name = g.current_user["full_name"]
        return jsonify({"message": f"Hello, {name}!", "status": "ok"})


class HealthController:
    @staticmethod
    def health():
        return jsonify({"status": "ok"})
