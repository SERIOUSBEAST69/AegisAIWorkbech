'use strict';

const ID_CARD = /[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/g;
const PHONE = /(?<!\d)1[3-9]\d{9}(?!\d)/g;
const BANK_CARD = /(?<!\d)\d{16,19}(?!\d)/g;
const COMPANY_CODE = /(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/g;

function detectTypes(text) {
  const value = String(text || '');
  const types = [];
  if (/[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/.test(value)) {
    types.push('id_card');
  }
  if (/(?<!\d)1[3-9]\d{9}(?!\d)/.test(value)) {
    types.push('phone');
  }
  if (/(?<!\d)\d{16,19}(?!\d)/.test(value)) {
    types.push('bank_card');
  }
  if (/(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/.test(value)) {
    types.push('company_code');
  }
  return types;
}

function maskIdCard(value) {
  if (!value || value.length < 10) return value;
  return value.slice(0, 6) + '******' + value.slice(-4);
}

function maskPhone(value) {
  if (!value || value.length !== 11) return value;
  return value.slice(0, 3) + '****' + value.slice(-4);
}

function maskBankCard(value) {
  if (!value || value.length < 10) return value;
  return value.slice(0, 4) + '****' + value.slice(-4);
}

function maskCompanyCode(value) {
  if (!value || value.length < 8) return value;
  return value.slice(0, 4) + '******' + value.slice(-4);
}

function maskContent(text) {
  const value = String(text || '');
  return value
    .replace(ID_CARD, (m) => maskIdCard(m))
    .replace(PHONE, (m) => maskPhone(m))
    .replace(BANK_CARD, (m) => maskBankCard(m))
    .replace(COMPANY_CODE, (m) => maskCompanyCode(m));
}

module.exports = {
  detectTypes,
  maskContent,
};
