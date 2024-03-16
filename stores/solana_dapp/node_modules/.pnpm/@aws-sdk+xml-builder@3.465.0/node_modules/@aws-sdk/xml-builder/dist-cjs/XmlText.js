"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.XmlText = void 0;
const escape_element_1 = require("./escape-element");
class XmlText {
    constructor(value) {
        this.value = value;
    }
    toString() {
        return (0, escape_element_1.escapeElement)("" + this.value);
    }
}
exports.XmlText = XmlText;
