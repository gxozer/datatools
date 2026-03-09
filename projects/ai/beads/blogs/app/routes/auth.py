from flask import Blueprint, request, jsonify
from flask_jwt_extended import create_access_token, create_refresh_token, jwt_required, get_jwt_identity
from app.extensions import db
from app.models.user import User
from app.schemas.user import user_schema

bp = Blueprint("auth", __name__, url_prefix="/auth")


@bp.post("/register")
def register():
    data = request.get_json() or {}
    if not all(k in data for k in ("username", "email", "password")):
        return jsonify({"error": "username, email, and password are required"}), 400
    if User.query.filter_by(username=data["username"]).first():
        return jsonify({"error": "Username already taken"}), 409
    if User.query.filter_by(email=data["email"]).first():
        return jsonify({"error": "Email already registered"}), 409

    user = User(username=data["username"], email=data["email"])
    user.set_password(data["password"])
    db.session.add(user)
    db.session.commit()
    return user_schema.dump(user), 201


@bp.post("/login")
def login():
    data = request.get_json() or {}
    if not all(k in data for k in ("email", "password")):
        return jsonify({"error": "email and password are required"}), 400

    user = User.query.filter_by(email=data["email"]).first()
    if not user or not user.check_password(data["password"]):
        return jsonify({"error": "Invalid credentials"}), 401

    return jsonify(
        access_token=create_access_token(identity=str(user.id)),
        refresh_token=create_refresh_token(identity=str(user.id)),
    ), 200


@bp.post("/refresh")
@jwt_required(refresh=True)
def refresh():
    return jsonify(access_token=create_access_token(identity=get_jwt_identity())), 200
