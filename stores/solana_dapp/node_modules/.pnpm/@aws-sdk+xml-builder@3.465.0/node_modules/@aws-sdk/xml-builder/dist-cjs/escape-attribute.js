"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.escapeAttribute = void 0;
function escapeAttribute(value) {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
exports.escapeAttribute = escapeAttribute;
