from flask import Blueprint, request, jsonify, abort
from flask_jwt_extended import jwt_required, get_jwt_identity
from app.extensions import db
from app.models.post import Post
from app.models.comment import Comment
from app.schemas.comment import comment_schema, comments_schema

bp = Blueprint("comments", __name__, url_prefix="/posts")


@bp.get("/<int:post_id>/comments")
def list_comments(post_id):
    Post.query.get_or_404(post_id)
    comments = Comment.query.filter_by(post_id=post_id).order_by(Comment.created_at).all()
    return jsonify(comments_schema.dump(comments))


@bp.post("/<int:post_id>/comments")
@jwt_required()
def create_comment(post_id):
    Post.query.get_or_404(post_id)
    data = request.get_json() or {}
    if "body" not in data:
        return jsonify({"error": "body is required"}), 400

    comment = Comment(body=data["body"], post_id=post_id, author_id=int(get_jwt_identity()))
    db.session.add(comment)
    db.session.commit()
    return comment_schema.dump(comment), 201


@bp.put("/<int:post_id>/comments/<int:comment_id>")
@jwt_required()
def update_comment(post_id, comment_id):
    Post.query.get_or_404(post_id)
    comment = Comment.query.filter_by(id=comment_id, post_id=post_id).first_or_404()
    if comment.author_id != int(get_jwt_identity()):
        abort(403)
    data = request.get_json() or {}
    if "body" in data:
        comment.body = data["body"]
    db.session.commit()
    return comment_schema.dump(comment)


@bp.delete("/<int:post_id>/comments/<int:comment_id>")
@jwt_required()
def delete_comment(post_id, comment_id):
    Post.query.get_or_404(post_id)
    comment = Comment.query.filter_by(id=comment_id, post_id=post_id).first_or_404()
    if comment.author_id != int(get_jwt_identity()):
        abort(403)
    db.session.delete(comment)
    db.session.commit()
    return "", 204
