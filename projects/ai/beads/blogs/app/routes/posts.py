from flask import Blueprint, request, jsonify, abort
from flask_jwt_extended import jwt_required, get_jwt_identity
from app.extensions import db
from app.models.post import Post
from app.schemas.post import post_schema, posts_schema

bp = Blueprint("posts", __name__, url_prefix="/posts")


@bp.get("/")
def list_posts():
    page = request.args.get("page", 1, type=int)
    pagination = Post.query.order_by(Post.created_at.desc()).paginate(page=page, per_page=20)
    return jsonify({
        "posts": posts_schema.dump(pagination.items),
        "total": pagination.total,
        "pages": pagination.pages,
        "page": pagination.page,
    })


@bp.post("/")
@jwt_required()
def create_post():
    data = request.get_json() or {}
    if not all(k in data for k in ("title", "body")):
        return jsonify({"error": "title and body are required"}), 400

    post = Post(title=data["title"], body=data["body"], author_id=int(get_jwt_identity()))
    db.session.add(post)
    db.session.commit()
    return post_schema.dump(post), 201


@bp.get("/<int:post_id>")
def get_post(post_id):
    return post_schema.dump(Post.query.get_or_404(post_id))


@bp.put("/<int:post_id>")
@jwt_required()
def update_post(post_id):
    post = Post.query.get_or_404(post_id)
    if post.author_id != int(get_jwt_identity()):
        abort(403)
    data = request.get_json() or {}
    if "title" in data:
        post.title = data["title"]
    if "body" in data:
        post.body = data["body"]
    db.session.commit()
    return post_schema.dump(post)


@bp.delete("/<int:post_id>")
@jwt_required()
def delete_post(post_id):
    post = Post.query.get_or_404(post_id)
    if post.author_id != int(get_jwt_identity()):
        abort(403)
    db.session.delete(post)
    db.session.commit()
    return "", 204
