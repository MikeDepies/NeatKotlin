const defaultTheme = require("tailwindcss/defaultTheme")
const colors = require('tailwindcss/colors')
module.exports = {
	purge: [],
	theme: {
		extend: {
			fontFamily: {
				sans: ["Inter var", ...defaultTheme.fontFamily.sans],
			},
			colors: {
				'light-blue': colors.lightBlue,
			}
		},
	},
	variants: {},
	plugins: [require('@tailwindcss/forms'),
	require('@tailwindcss/typography'),
	require('@tailwindcss/aspect-ratio')],
}
